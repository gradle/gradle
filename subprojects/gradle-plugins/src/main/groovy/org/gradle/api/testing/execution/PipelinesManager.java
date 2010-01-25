/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.testing.execution.control.server.ExternalControlServerFactory;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.policies.ForkPolicy;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyConfig;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyRegister;
import org.gradle.api.testing.reporting.TestReportProcessor;
import org.gradle.util.ConditionWaitHandle;
import org.gradle.util.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class PipelinesManager {
    private final PipelineFactory pipelineFactory;
    private final ForkControl forkControl;
    private final TestReportProcessor testReportProcessor;

    private final Lock lock;
    private final List<QueueingPipeline> orderedPipelines;
    private final List<Pipeline> notStopped;
    private final Condition allStoppedCondition;
    private final ExternalControlServerFactory serverFactory = new ExternalControlServerFactory();

    private int pipelineIdSequence;

    public PipelinesManager(PipelineFactory pipelineFactory, ForkControl forkControl, TestReportProcessor testReportProcessor) {
        this.pipelineFactory = pipelineFactory;
        this.forkControl = forkControl;
        this.testReportProcessor = testReportProcessor;

        lock = new ReentrantLock();
        orderedPipelines = new ArrayList<QueueingPipeline>();

        notStopped = new CopyOnWriteArrayList<Pipeline>();
        allStoppedCondition = lock.newCondition();

        pipelineIdSequence = 0;
    }

    public void initialize(NativeTest testTask) {

        final Map<String, PipelineConfig> pipelineConfigs = testTask.getPipelineConfigs();
        if (pipelineConfigs.isEmpty()) {
            final PipelineConfig defaultPipelineConfig = new PipelineConfig("default");
            pipelineConfigs.put(defaultPipelineConfig.getName(), defaultPipelineConfig);
        }

        for (final PipelineConfig pipelineConfig : pipelineConfigs.values()) {
            final QueueingPipeline pipeline = addPipeline(pipelineConfig);

            // initialize fork policy
            final ForkPolicyConfig forkPolicyConfig = pipelineConfig.getForkPolicyConfig();
            final ForkPolicy forkPolicy = ForkPolicyRegister.getForkPolicy(forkPolicyConfig.getPolicyName());
            final ForkPolicyInstance forkPolicyInstance = forkPolicy.getForkPolicyInstance(pipeline, forkControl, serverFactory);
            pipeline.setForkPolicyInstance(forkPolicyInstance);
            pipeline.getForkPolicyInstance().initialize();

            // initialize refork controller
            pipeline.getReforkController().initialize(pipeline);
        }
    }

    public List<Pipeline> getPipelines() {
        lock.lock();
        try {
            return new ArrayList<Pipeline>(orderedPipelines);
        } finally {
            lock.unlock();
        }
    }

    private QueueingPipeline addPipeline(PipelineConfig pipelineConfig) {
        lock.lock();
        try {
            final QueueingPipeline pipeline = pipelineFactory.createPipeline(this, pipelineIdSequence++,
                    pipelineConfig, testReportProcessor);

            orderedPipelines.add(pipeline);
            notStopped.add(pipeline);

            return pipeline;
        } finally {
            lock.unlock();
        }
    }

    public void waitForExecutionEnd() {
        ThreadUtils.interleavedConditionWait(lock, allStoppedCondition, 100L, TimeUnit.MILLISECONDS,
                new ConditionWaitHandle() {
                    public boolean checkCondition() {
                        return notStopped.isEmpty();
                    }

                    public void conditionMatched() {
                        // nothing - just return
                    }
                });
        serverFactory.stop();
    }

    public void stopped(Pipeline pipeline) {
        lock.lock();
        try {
            notStopped.remove(pipeline);
            if (notStopped.isEmpty()) {
                allStoppedCondition.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public void pipelineSplittingEnded() {
        lock.lock();
        try {
            for (final QueueingPipeline pipeline : orderedPipelines) {
                pipeline.pipelineSplittingEnded();
            }
        } finally {
            lock.unlock();
        }
    }
}
