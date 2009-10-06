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
