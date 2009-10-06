package org.gradle.api.testing.execution;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.control.refork.ReforkController;
import org.gradle.api.testing.execution.control.refork.ReforkControllerImpl;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public class Pipeline {
    private final PipelinesManager manager;
    private final int id;
    private final NativeTest testTask;
    private final BlockingQueue<TestClassRunInfo> runInfoQueue;
    private final BlockingQueueItemProducer<TestClassRunInfo> runInfoQueueProducer;
    private final PipelineConfig config;
    private ForkPolicyInstance forkPolicyInstance;
    private PipelineDispatcher dispatcher;
    private final ReforkController reforkController;
    private final AtomicBoolean pipelineSplittingEnded = new AtomicBoolean(Boolean.FALSE);

    public Pipeline(PipelinesManager manager, int id, NativeTest testTask, PipelineConfig config) {
        this.manager = manager;
        this.id = id;
        this.testTask = testTask;
        this.config = config;
        this.runInfoQueue = new ArrayBlockingQueue<TestClassRunInfo>(1000);
        this.runInfoQueueProducer = new BlockingQueueItemProducer<TestClassRunInfo>(runInfoQueue, 100L, TimeUnit.MILLISECONDS);
        this.reforkController = new ReforkControllerImpl();
    }

    public int getId() {
        return id;
    }

    public PipelineConfig getConfig() {
        return config;
    }

    public void addTestClassRunInfo(final TestClassRunInfo testClassRunInfo) {
        runInfoQueueProducer.produce(testClassRunInfo);
    }

    public BlockingQueue<TestClassRunInfo> getRunInfoQueue() {
        return runInfoQueue;
    }

    public NativeTest getTestTask() {
        return testTask;
    }

    public void setDispatcher(PipelineDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public PipelineDispatcher getDispatcher() {
        return dispatcher;
    }

    public ReforkController getReforkController() {
        return reforkController;
    }

    public ForkPolicyInstance getForkPolicyInstance() {
        return forkPolicyInstance;
    }

    public void setForkPolicyInstance(ForkPolicyInstance forkPolicyInstance) {
        this.forkPolicyInstance = forkPolicyInstance;
    }

    public void pipelineSplittingEnded() {
        pipelineSplittingEnded.set(Boolean.TRUE);
    }

    public boolean isPipelineSplittingEnded() {
        return pipelineSplittingEnded.get();
    }

    public void stopped() {
        forkPolicyInstance.stop();
        manager.stopped(this);
    }
}
