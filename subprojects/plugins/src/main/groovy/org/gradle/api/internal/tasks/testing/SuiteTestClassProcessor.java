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

import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.internal.TimeProvider;

public class SuiteTestClassProcessor implements TestClassProcessor {
    private final TestClassProcessor processor;
    private final TimeProvider timeProvider;
    private final TestDescriptorInternal suiteDescriptor;
    private TestResultProcessor resultProcessor;

    public SuiteTestClassProcessor(TestDescriptorInternal suiteDescriptor, TestClassProcessor processor,
                                   TimeProvider timeProvider) {
        this.suiteDescriptor = suiteDescriptor;
        this.processor = processor;
        this.timeProvider = timeProvider;
    }

    public void startProcessing(TestResultProcessor testResultProcessor) {
        try {
            resultProcessor = new AttachParentTestResultProcessor(testResultProcessor);
            resultProcessor.started(suiteDescriptor, new TestStartEvent(timeProvider.getCurrentTime()));
            processor.startProcessing(resultProcessor);
        } catch (Throwable t) {
            resultProcessor.failure(suiteDescriptor.getId(), new TestSuiteExecutionException(String.format(
                    "Could not start %s.", suiteDescriptor), t));
        }
    }

    public void processTestClass(TestClassRunInfo testClass) {
        try {
            processor.processTestClass(testClass);
        } catch (Throwable t) {
            resultProcessor.failure(suiteDescriptor.getId(), new TestSuiteExecutionException(String.format(
                    "Could not execute test class '%s'.", testClass.getTestClassName()), t));
        }
    }

    public void stop() {
        try {
            processor.stop();
        } catch (Throwable t) {
            resultProcessor.failure(suiteDescriptor.getId(), new TestSuiteExecutionException(String.format(
                    "Could not complete execution for %s.", suiteDescriptor), t));
        } finally {
            resultProcessor.completed(suiteDescriptor.getId(), new TestCompleteEvent(timeProvider.getCurrentTime()));
        }
    }
}
