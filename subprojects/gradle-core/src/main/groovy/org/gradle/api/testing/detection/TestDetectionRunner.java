package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestFrameworkInstance;

import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * @author Tom Eyckmans
 */
public class TestDetectionRunner implements Runnable {

    private final NativeTest testTask;
    private final BlockingQueue<TestClassRunInfo> testDetectionQueue;

    public TestDetectionRunner(NativeTest testTask, BlockingQueue<TestClassRunInfo> testDetectionQueue) {
        if (testTask == null) throw new IllegalArgumentException("testTask == null!");
        if (testDetectionQueue == null) throw new IllegalArgumentException("testDetectionQueue == null!");

        this.testTask = testTask;
        this.testDetectionQueue = testDetectionQueue;
    }

    public void run() {
        final TestFrameworkInstance testFrameworkInstance = testTask.getTestFramework();

        testFrameworkInstance.prepare(testTask.getProject(), testTask, new TestClassRunInfoProducingTestClassReceiver(testDetectionQueue));

        final Set<String> includes = testTask.getIncludes();
        final Set<String> excludes = testTask.getExcludes();

        final TestClassScanner testClassScanner = new TestClassScanner(
                testTask.getTestClassesDir(),
                includes, excludes,
                testFrameworkInstance,
                testTask.isScanForTestClasses()
        );

        testClassScanner.getTestClassNames();
    }

    public void stopDetecting() {
        // currently not implemented because detection is very fast
    }
}
