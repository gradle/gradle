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

import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.internal.Factory;

public class RestartEveryNTestDefinitionProcessor<D extends TestDefinition> implements TestDefinitionProcessor<D> {
    private final Factory<TestDefinitionProcessor<D>> factory;
    private final long restartEvery;
    private long testCount;
    private TestResultProcessor resultProcessor;
    private volatile boolean stoppedNow;
    private volatile TestDefinitionProcessor<D> processor;

    public RestartEveryNTestDefinitionProcessor(Factory<TestDefinitionProcessor<D>> factory, long restartEvery) {
        this.factory = factory;
        this.restartEvery = restartEvery;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    @Override
    public void processTestDefinition(D testDefinition) {
        if (stoppedNow) {
            return;
        }

        if (processor == null) {
            processor = factory.create();
            processor.startProcessing(resultProcessor);
        }
        processor.processTestDefinition(testDefinition);
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

    @Override
    public void stopNow() {
        stoppedNow = true;
        TestDefinitionProcessor<D> toStop = processor;
        if (toStop != null) {
            toStop.stopNow();
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
