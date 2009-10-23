package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.TestOrchestratorFactory;
import org.gradle.api.testing.fabric.*;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestDetectionOrchestratorFactory implements TestDetectionOrchestratorFactory {
    private final TestOrchestratorFactory testOrchestratorFactory;
    private final TestClassRunInfoFactory testClassRunInfoFactory;
    private final BlockingQueueItemProducer<TestClassRunInfo> testDetectionQueueProducer;

    public DefaultTestDetectionOrchestratorFactory(final TestOrchestratorFactory testOrchestratorFactory) {
        if ( testOrchestratorFactory == null ) throw new IllegalArgumentException("testOrchestratorFactory == null!");

        this.testOrchestratorFactory = testOrchestratorFactory;

        testClassRunInfoFactory = new DefaultTestClassRunInfoFactory();
        final BlockingQueue<TestClassRunInfo> testDetectionQueue = testOrchestratorFactory.getTestDetectionQueue();
        testDetectionQueueProducer = new BlockingQueueItemProducer<TestClassRunInfo>(testDetectionQueue, 100L, TimeUnit.MILLISECONDS);
    }

    /**
     *
     * @return
     */
    public TestDetectionRunner createDetectionRunner() {
        final NativeTest testTask = testOrchestratorFactory.getTestTask();
        final TestFrameworkInstance testFrameworkInstance = testTask.getTestFramework();

        final TestClassProcessor testClassProcessor = new QueueItemProducingTestClassProcessor(testDetectionQueueProducer, testClassRunInfoFactory);

        final TestFrameworkDetector detector = testFrameworkInstance.getDetector();

        final TestClassScanner testClassScanner = new DefaultTestClassScanner(testTask, detector, testClassProcessor);

        return new TestDetectionRunner(testClassScanner);
    }

    public Thread createDetectionThread(TestDetectionRunner detectionRunner) {
        return new Thread(detectionRunner);
    }
}
