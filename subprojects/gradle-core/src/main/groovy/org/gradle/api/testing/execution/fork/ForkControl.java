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
    private final AtomicInteger maximumNumberOfForks;
    private final AtomicInteger startedForks;

    private final List<ForkInfo> pendingForkStartRequests;

    private final Lock runningHandlesLock;
    private final Map<Integer, Map<Integer, ForkInfo>> forkHandles;

    public ForkControl(int maximumNumberOfForks) {
        forkControlLock = new ReentrantLock();

        this.forkIdSeq = new AtomicInteger(0);
        this.maximumNumberOfForks = new AtomicInteger(maximumNumberOfForks);
        this.startedForks = new AtomicInteger(0);

        this.pendingForkStartRequests = new CopyOnWriteArrayList<ForkInfo>();
        forkHandles = new HashMap<Integer, Map<Integer, ForkInfo>>();
        runningHandlesLock = new ReentrantLock();
    }

    public void setMaximumNumberOfForks(int maximumNumberOfForks) {
        forkControlLock.lock();
        try {
            this.maximumNumberOfForks.set(maximumNumberOfForks);
        }
        finally {
            forkControlLock.unlock();
        }
    }

    public int getNextForkId() {
        return forkIdSeq.incrementAndGet();
    }

    public ForkInfo createForkInfo(int forkId, Pipeline pipeline) {
        final ForkPolicyInstance forkPolicyInstance = pipeline.getForkPolicyInstance();

        final ForkInfo forkInfo = new ForkInfo(forkId, pipeline);

        forkPolicyInstance.initializeFork(forkInfo);

        return forkInfo;
    }

    public void requestForkStart(ForkInfo forkInfo) {
        forkControlLock.lock();
        try {
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
            final ForkPolicyInstance forkPolicyInstance = forkInfo.getPipeline().getForkPolicyInstance();

            runningHandlesLock.lock();
            try {
                Map<Integer, ForkInfo> forks = forkHandles.get(forkInfo.getPipeline().getId());
                if (forks == null) {
                    forks = new HashMap<Integer, ForkInfo>();
                }
                forks.put(forkInfo.getId(), forkInfo);
                forkHandles.put(forkInfo.getPipeline().getId(), forks);
            }
            finally {
                runningHandlesLock.unlock();
            }

            forkPolicyInstance.startFork(forkInfo);

            startedForks.incrementAndGet();
        }
        finally {
            forkControlLock.unlock();
        }
    }

    boolean startAllowed() {
        forkControlLock.lock();
        try {
            return startedForks.get() <= maximumNumberOfForks.get();
        }
        finally {
            forkControlLock.unlock();
        }
    }

    void lowerStartedForksCount() {
        forkControlLock.lock();
        try {
            startedForks.decrementAndGet();
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
        try {
            forkHandles.get(pipelineId).get(forkId).started();
        }
        catch (Throwable t) {
            logger.error("failed to properly signal fork started for fork {} started for pipeline with id {}", forkId, pipelineId);
        }
    }

    private void forkStopped(int pipelineId, int forkId, Throwable cause) {
        logger.warn("fork {} stopped for pipeline with id {}", forkId, pipelineId);

        runningHandlesLock.lock();
        try {
            if (!getForkInfo(pipelineId, forkId).isRestarting()) {
                try {
                    forkHandles.get(pipelineId).get(forkId).stopped(cause);
                }
                catch (Throwable t) {

                }
                startPending();
            }
            lowerStartedForksCount();
        }
        finally {
            runningHandlesLock.unlock();
        }
    }

    public void forkFinished(int pipelineId, int forkId) {
        forkStopped(pipelineId, forkId, null);
    }

    public void forkAborted(int pipelineId, int forkId) {
        forkStopped(pipelineId, forkId, null);
    }

    public void forkFailed(int pipelineId, int forkId, Throwable cause) {
        forkStopped(pipelineId, forkId, cause);
    }

    public ForkInfo getForkInfo(int pipelineId, int forkId) {
        runningHandlesLock.lock();
        try {
            return forkHandles.get(pipelineId).get(forkId);
        }
        finally {
            runningHandlesLock.unlock();
        }
    }

    public List<ForkInfo> getForkInfos(int pipelineId) {
        runningHandlesLock.lock();
        try {
            return Collections.unmodifiableList(new ArrayList<ForkInfo>(forkHandles.get(pipelineId).values()));
        }
        finally {
            runningHandlesLock.unlock();
        }
    }
}
