package org.gradle.api.testing.pipelinesplit;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.PipelinesManager;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.pipelinesplit.policies.*;
import org.gradle.util.ConditionWaitHandle;
import org.gradle.util.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Tom Eyckmans
 */
public class TestPipelineSplitOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(TestPipelineSplitOrchestrator.class);

    private final BlockingQueue<TestClassRunInfo> testDetectionQueue;

    private final ExecutorService pipelineSplitThreadPool;

    private final List<PipelineSplitWorker> runningWorkers;
    private final Lock runningWorkersLock;
    private final Condition allWorkersStopped;

    public TestPipelineSplitOrchestrator(final BlockingQueue<TestClassRunInfo> testDetectionQueue) {
        this.testDetectionQueue = testDetectionQueue;

        pipelineSplitThreadPool = ThreadUtils.newFixedThreadPool(2);

        runningWorkers = new ArrayList<PipelineSplitWorker>();
        runningWorkersLock = new ReentrantLock();
        allWorkersStopped = runningWorkersLock.newCondition();
    }

    public void startPipelineSplitting(final PipelinesManager pipelinesManager) {
        final Map<SplitPolicyMatcher, Pipeline> pipelineMatchers = new HashMap<SplitPolicyMatcher, Pipeline>();
        final List<SplitPolicyMatcher> splitPolicyMatchers = new ArrayList<SplitPolicyMatcher>();

        for (final Pipeline pipeline : pipelinesManager.getPipelines()) {
            final PipelineConfig pipelineConfig = pipeline.getConfig();
            final SplitPolicyConfig splitPolicyConfig = pipelineConfig.getSplitPolicyConfig();

            final SplitPolicy splitPolicy = SplitPolicyRegister.getSplitPolicy(splitPolicyConfig.getPolicyName());
            final SplitPolicyInstance splitPolicyInstance = splitPolicy.getSplitPolicyInstance(pipelineConfig);

            splitPolicyInstance.prepare();

            final SplitPolicyMatcher matcher = splitPolicyInstance.createSplitPolicyMatcher();

            pipelineMatchers.put(matcher, pipeline);
            splitPolicyMatchers.add(matcher);
        }

        for (int i = 0; i < 2; i++) {
            pipelineSplitThreadPool.submit(
                    new PipelineSplitWorker(
                            this,
                            testDetectionQueue,
                            100L,
                            TimeUnit.MILLISECONDS,
                            Collections.unmodifiableList(splitPolicyMatchers),
                            Collections.unmodifiableMap(pipelineMatchers)));
        }
    }

    public void waitForPipelineSplittingEnded() {
        for (final PipelineSplitWorker pipelineSplitWorker : runningWorkers) {
            pipelineSplitWorker.stopConsuming();
        }

        ThreadUtils.interleavedConditionWait(
                runningWorkersLock, allWorkersStopped,
                100L, TimeUnit.MILLISECONDS,
                new ConditionWaitHandle() {
                    public boolean checkCondition() {
                        return runningWorkers.isEmpty();
                    }

                    public void conditionMatched() {
                        ThreadUtils.shutdown(pipelineSplitThreadPool);
                    }
                }
        );
    }

    void splitWorkerStarted(PipelineSplitWorker worker) {
        runningWorkersLock.lock();
        try {
            runningWorkers.add(worker);
        }
        finally {
            runningWorkersLock.unlock();
        }
    }

    void splitWorkerStopped(PipelineSplitWorker worker) {
        runningWorkersLock.lock();
        try {
            final boolean removed = runningWorkers.remove(worker);

            if (!removed) {
                logger.warn("splitWorkerStopped called for an unrelated split worker");
            }

            if (runningWorkers.isEmpty())
                allWorkersStopped.signal();
        }
        finally {
            runningWorkersLock.unlock();
        }
    }
}
