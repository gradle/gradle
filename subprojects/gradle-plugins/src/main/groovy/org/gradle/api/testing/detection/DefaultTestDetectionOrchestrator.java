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

package org.gradle.api.testing.detection;

import org.gradle.util.ThreadUtils;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Uses a single thread to execute the test class detection.
 *
 * @author Tom Eyckmans
 */
public class DefaultTestDetectionOrchestrator implements TestDetectionOrchestrator {
    private final Lock detectionRunStateLock;
    private Thread detectionThread;
    private final Runnable scanner;

    public DefaultTestDetectionOrchestrator(Runnable scanner) {
        this.scanner = scanner;
        this.detectionRunStateLock = new ReentrantLock();
    }

    public void startDetection() {
        detectionRunStateLock.lock();
        try {
            if (detectionThread == null) {
                detectionThread = new Thread(scanner);
                detectionThread.start();
            } else {
                throw new IllegalStateException("detection already started");
            }
        } finally {
            detectionRunStateLock.unlock();
        }
    }

    public void waitForDetectionEnd() {
        detectionRunStateLock.lock();
        try {
            if (detectionThread != null) {
                ThreadUtils.join(detectionThread);
                detectionThread = null;
            }
        } finally {
            detectionRunStateLock.unlock();
        }
    }
}
