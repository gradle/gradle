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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.internal.Factory;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

public class RestartEveryNTestClassProcessor implements TestClassProcessor {
    private final Factory<TestClassProcessor> factory;
    private final long restartEvery;
    private long testCount;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;

    public RestartEveryNTestClassProcessor(Factory<TestClassProcessor> factory, long restartEvery) {
        this.factory = factory;
        this.restartEvery = restartEvery;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        if (processor == null) {
            processor = factory.create();
            processor.startProcessing(resultProcessor);
        }
        processor.processTestClass(testClass);
        testCount++;
        if (testCount == restartEvery) {
            endBatch();
        }
    }

    @Override
    public void stop() {
        if (processor != null) {
            endBatch();
        }
    }

    private void endBatch() {
        try {
            processor.stop();
        } finally {
            processor = null;
            testCount = 0;
        }
    }
}
