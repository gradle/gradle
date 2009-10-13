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
import org.gradle.util.ThreadUtils;

import java.util.concurrent.BlockingQueue;

/**
 * @author Tom Eyckmans
 */
public class TestDetectionOrchestrator {
    private final BlockingQueue<TestClassRunInfo> testDetectionQueue;

    private TestDetectionRunner detectionRunner;
    private Thread detectionThread;

    public TestDetectionOrchestrator(final BlockingQueue<TestClassRunInfo> testDetectionQueue) {
        this.testDetectionQueue = testDetectionQueue;
    }

    public void startDetection(NativeTest testTask) {
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
