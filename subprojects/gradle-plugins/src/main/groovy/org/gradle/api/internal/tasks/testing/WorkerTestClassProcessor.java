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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.util.TimeProvider;

public class WorkerTestClassProcessor implements TestClassProcessor {
    private final TestClassProcessor processor;
    private final TimeProvider timeProvider;
    private final TestInternalDescriptor thisTest;
    private TestResultProcessor resultProcessor;

    public WorkerTestClassProcessor(TestClassProcessor processor, Object workerSuiteId, String workerDisplayName,
                                    TimeProvider timeProvider) {
        this.processor = processor;
        this.timeProvider = timeProvider;
        thisTest = new DefaultTestSuiteDescriptor(workerSuiteId, workerDisplayName);
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
        resultProcessor.started(thisTest, new TestStartEvent(timeProvider.getCurrentTime()));

        try {
            processor.startProcessing(resultProcessor);
        } catch(Throwable t) {
            resultProcessor.addFailure(thisTest.getId(), t);
        }
    }

    public void processTestClass(TestClassRunInfo testClass) {
        try {
            processor.processTestClass(testClass);
        } catch(Throwable t) {
            resultProcessor.addFailure(thisTest.getId(), t);
        }
    }

    public void endProcessing() {
        try {
            processor.endProcessing();
        } catch(Throwable t) {
            resultProcessor.addFailure(thisTest.getId(), t);
        } finally {
            resultProcessor.completed(thisTest.getId(), new TestCompleteEvent(timeProvider.getCurrentTime()));
        }
    }
}
