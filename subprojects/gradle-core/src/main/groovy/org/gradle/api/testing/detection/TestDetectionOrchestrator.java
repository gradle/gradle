package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.ThreadUtils;

import java.util.concurrent.BlockingQueue;

/**
 * @author Tom Eyckmans
 */
public class TestDetectionOrchestrator {
    private final NativeTest testTask;
    private final BlockingQueue<TestClassRunInfo> testDetectionQueue;

    private TestDetectionRunner detectionRunner;
    private Thread detectionThread;

    public TestDetectionOrchestrator(final NativeTest testTask, final BlockingQueue<TestClassRunInfo> testDetectionQueue) {
        this.testTask = testTask;
        this.testDetectionQueue = testDetectionQueue;
    }

    public void startDetection() {
        detectionRunner = new TestDetectionRunner(testTask, testDetectionQueue);
        detectionThread = new Thread(detectionRunner);
        detectionThread.start();
    }

    public void stopDetection() {
        if (detectionRunner != null) {
            detectionRunner.stopDetecting();

            waitForDetectionEnd();
        }
    }

    public void waitForDetectionEnd() {
        if (detectionThread != null)
            ThreadUtils.join(detectionThread);
    }
}
