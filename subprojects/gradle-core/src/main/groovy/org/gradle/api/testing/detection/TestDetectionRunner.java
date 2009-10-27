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

/**
 * Runabble around the test class scanner executeScan call.
 *
 * @author Tom Eyckmans
 */
public class TestDetectionRunner implements Runnable {

    private final TestClassScanner testClassScanner;

    public TestDetectionRunner(final TestClassScanner testClassScanner) {
        if (testClassScanner == null) throw new IllegalArgumentException("testClassScanner == null!");

        this.testClassScanner = testClassScanner;
    }

    public void run() {
        testClassScanner.executeScan();
    }

    public void stopDetecting() {
        // currently not implemented because detection is very fast
    }
}
