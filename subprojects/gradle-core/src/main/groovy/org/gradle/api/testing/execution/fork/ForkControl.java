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
package org.gradle.api.testing.execution.fork;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class ForkControl {
    private static final Logger logger = LoggerFactory.getLogger(ForkControl.class);

    private final Lock forkControlLock;
    private final AtomicInteger forkIdSeq;
    private final int maximumNumberOfForks;
    private int startedForks;

    private final List<ForkInfo> pendingForkStartRequests;

    private final Map<Integer, Map<Integer, ForkInfo>> forkHandles;

    public ForkControl(int maximumNumberOfForks) {
        forkControlLock = new ReentrantLock();

        this.forkIdSeq = new AtomicInteger(0);
        this.maximumNumberOfForks = maximumNumberOfForks;
        this.startedForks = 0;

        this.pendingForkStartRequests = new CopyOnWriteArrayList<ForkInfo>();
        forkHandles = new HashMap<Integer, Map<Integer, ForkInfo>>();
    }

    private int getNextForkId() {
        return forkIdSeq.incrementAndGet();
    }

    private ForkInfo createForkInfo(int forkId, Pipeline pipeline) {
        final ForkPolicyInstance forkPolicyInstance = pipeline.getForkPolicyInstance();

        final ForkInfo forkInfo = new ForkInfo(forkId, pipeline);

        forkInfo.setPolicyInfo(forkPolicyInstance.createForkPolicyForkInfo());

        forkPolicyInstance.prepareFork(forkInfo);

        return forkInfo;
    }

    public ForkInfo createForkInfo(Pipeline pipeline)
    {
        forkControlLock.lock();
        try {
            final int pipelineId = pipeline.getId();
            final int forkId = getNextForkId();
            final ForkInfo forkInfo = createForkInfo(forkId, pipeline);

            Map<Integer, ForkInfo> pipelineForks = forkHandles.get(pipelineId);
            if ( pipelineForks == null ) {
                pipelineForks = new HashMap<Integer, ForkInfo>();
            }
            pipelineForks.put(forkId, forkInfo);
            forkHandles.put(pipelineId, pipelineForks);

            return forkInfo;
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public void requestForkStart(ForkInfo forkInfo)
    {
        requestForkStart(forkInfo.getPipeline().getId(), forkInfo.getId());
    }

    public void requestForkStart(int pipelineId, int forkId) {
        forkControlLock.lock();
        try {
            final ForkInfo forkInfo = forkHandles.get(pipelineId).get(forkId);
            if (startAllowed()) {
                startFork(forkInfo);
            } else {
                pendingForkStartRequests.add(forkInfo);
            }
        }
        finally {
            forkControlLock.unlock();
        }
    }

    void startFork(ForkInfo forkInfo) {
        forkControlLock.lock();
        try {
            forkInfo.starting();

            final ForkPolicyInstance forkPolicyInstance = forkInfo.getPipeline().getForkPolicyInstance();

            Map<Integer, ForkInfo> forks = forkHandles.get(forkInfo.getPipeline().getId());
            if (forks == null) {
                forks = new HashMap<Integer, ForkInfo>();
            }
            forks.put(forkInfo.getId(), forkInfo);
            forkHandles.put(forkInfo.getPipeline().getId(), forks);

            forkPolicyInstance.startFork(forkInfo);

            startedForks++;
        }
        finally {
            forkControlLock.unlock();
        }
    }

    boolean startAllowed() {
        forkControlLock.lock();
        try {
            return startedForks < maximumNumberOfForks;
        }
        finally {
            forkControlLock.unlock();
        }
    }

    void startPending() {
        forkControlLock.lock();
        try {
            final Iterator<ForkInfo> pending = pendingForkStartRequests.iterator();
            while (startAllowed() && pending.hasNext()) {
                startFork(pending.next());
            }
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public void forkStarted(int pipelineId, int forkId) {
        logger.warn("fork {} started for pipeline with id {}", forkId, pipelineId);
    }

    public void forkFinished(int pipelineId, int forkId) {
        logger.warn("fork {} finished for pipeline with id {}", forkId, pipelineId);

        forkControlLock.lock();
        try {
            startedForks--;

            forkHandles.get(pipelineId).get(forkId).finished();

            if (!forkHandles.get(pipelineId).get(forkId).isRestarting()) {
                startPending();
            }
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public void forkAborted(int pipelineId, int forkId) {
        logger.warn("fork {} aborted for pipeline with id {}", forkId, pipelineId);

        forkControlLock.lock();
        try {
            startedForks--;

            forkHandles.get(pipelineId).get(forkId).aborted();

            startPending();
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public void forkFailed(int pipelineId, int forkId, Throwable cause) {
        logger.warn("fork {} failed for pipeline with id {}", forkId, pipelineId);

        forkControlLock.lock();
        try {
            startedForks--;

            forkHandles.get(pipelineId).get(forkId).failed(cause);
            
            startPending();
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public List<ForkInfo> getForkInfos(int pipelineId) {
        forkControlLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<ForkInfo>(forkHandles.get(pipelineId).values()));
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public void setRestarting(int pipelineId, int forkId, boolean restarting) {
        forkControlLock.lock();
        try {
            forkHandles.get(pipelineId).get(forkId).setRestarting(restarting);
        }
        finally {
            forkControlLock.unlock();
        }
    }
}
