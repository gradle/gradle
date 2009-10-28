/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.testing.execution;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.policies.ForkPolicy;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyConfig;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyRegister;
import org.gradle.util.ConditionWaitHandle;
import org.gradle.util.ThreadUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/**
 * @author Tom Eyckmans
 */
public class PipelinesManager {
    private final PipelineFactory pipelineFactory;
    private final ForkControl forkControl;

    private final ReadWriteLock pipelinesLock;
    private final Map<Integer, Pipeline> pipelinesInfo;
    private final List<Pipeline> orderedPipelines;

    private final List<Pipeline> notStopped;
    private final Lock allStoppedLock;
    private final Condition allStoppedCondition;

    private final AtomicInteger pipelineIdSequence;

    public PipelinesManager(PipelineFactory pipelineFactory, ForkControl forkControl) {
        this.pipelineFactory = pipelineFactory;
        this.forkControl = forkControl;

        pipelinesLock = new ReentrantReadWriteLock();
        pipelinesInfo = new HashMap<Integer, Pipeline>();
        orderedPipelines = new ArrayList<Pipeline>();

        notStopped = new CopyOnWriteArrayList<Pipeline>();
        allStoppedLock = new ReentrantLock();
        allStoppedCondition = allStoppedLock.newCondition();

        pipelineIdSequence = new AtomicInteger(0);
    }

    public void initialize(NativeTest testTask) {

        final Map<String, PipelineConfig> pipelineConfigs = testTask.getPipelineConfigs();
        if (pipelineConfigs.isEmpty()) {
            final PipelineConfig defaultPipelineConfig = new PipelineConfig("default");
            pipelineConfigs.put(defaultPipelineConfig.getName(), defaultPipelineConfig);
        }

        for (final PipelineConfig pipelineConfig : pipelineConfigs.values()) {
            final Pipeline pipeline = addPipeline(pipelineConfig);

            // initialize fork policy
            final ForkPolicyConfig forkPolicyConfig = pipelineConfig.getForkPolicyConfig();
            final ForkPolicy forkPolicy = ForkPolicyRegister.getForkPolicy(forkPolicyConfig.getPolicyName());
            final ForkPolicyInstance forkPolicyInstance = forkPolicy.getForkPolicyInstance(pipeline, forkControl);
            pipeline.setForkPolicyInstance(forkPolicyInstance);
            pipeline.getForkPolicyInstance().initialize();

            // initialize refork controller
            pipeline.getReforkController().initialize(testTask, pipelineConfig);
        }
    }

    private int getNextPipelineId() {
        return pipelineIdSequence.incrementAndGet();
    }

    public Pipeline getPipeline(int pipelineId) {
        pipelinesLock.readLock().lock();
        try {
            return pipelinesInfo.get(pipelineId);
        }
        finally {
            pipelinesLock.readLock().unlock();
        }
    }

    public List<Pipeline> getPipelines() {
        pipelinesLock.readLock().lock();
        try {
            return Collections.unmodifiableList(orderedPipelines);
        }
        finally {
            pipelinesLock.readLock().unlock();
        }
    }

    public Pipeline addPipeline(PipelineConfig pipelineConfig) {
        pipelinesLock.writeLock().lock();
        try {
            final Pipeline pipeline = pipelineFactory.createPipeline(this, getNextPipelineId(), pipelineConfig);

            pipelinesInfo.put(pipeline.getId(), pipeline);
            orderedPipelines.add(pipeline);

            notStopped.add(pipeline);

            return pipeline;
        }
        finally {
            pipelinesLock.writeLock().unlock();
        }
    }

    public void waitForExecutionEnd() {
        ThreadUtils.interleavedConditionWait(
                allStoppedLock,
                allStoppedCondition,
                100L, TimeUnit.MILLISECONDS,
                new ConditionWaitHandle() {
                    public boolean checkCondition() {
                        return notStopped.isEmpty();
                    }

                    public void conditionMatched() {
                        // nothing - just return
                    }
                }
        );
    }

    public void stopped(Pipeline pipeline) {
        allStoppedLock.lock();
        try {
            for ( final PipelineListener listener : pipeline.getListeners() ) {
                listener.pipelineStopped(pipeline);
            }

            notStopped.remove(pipeline);
            if (notStopped.isEmpty())
                allStoppedCondition.signal();
        }
        finally {
            allStoppedLock.unlock();
        }
    }

    public void pipelineSplittingEnded() {
        pipelinesLock.readLock().lock();
        try {
            for (final Pipeline pipeline : orderedPipelines) {
                pipeline.pipelineSplittingEnded();
            }
        }
        finally {
            pipelinesLock.readLock().unlock();
        }
    }
}
