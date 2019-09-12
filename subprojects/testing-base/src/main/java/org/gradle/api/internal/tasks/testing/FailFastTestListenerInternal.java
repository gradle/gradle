/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.results.DefaultTestResult;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

/**
 * {@code TestListenerInternal} that causes the {@link TestExecuter} to stop at the first failed test
 */
public class FailFastTestListenerInternal implements TestListenerInternal {
    private final TestExecuter testExecuter;
    private final TestListenerInternal delegate;
    private boolean failed;

    public FailFastTestListenerInternal(TestExecuter testExecuter, TestListenerInternal delegate) {
        this.testExecuter = testExecuter;
        this.delegate = delegate;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        delegate.started(testDescriptor, startEvent);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        TestResult delegateResult = testResult;
        if (failed) {
            if (testDescriptor.isComposite()) {
                delegateResult = new DefaultTestResult(TestResult.ResultType.FAILURE, testResult.getStartTime(), testResult.getEndTime(), testResult.getTestCount(), testResult.getSuccessfulTestCount(), testResult.getFailedTestCount(), testResult.getExceptions(), testResult.getOutput());
            } else {
                delegateResult = new DefaultTestResult(TestResult.ResultType.SKIPPED, testResult.getStartTime(), testResult.getEndTime(), testResult.getTestCount(), testResult.getSuccessfulTestCount(), testResult.getFailedTestCount(), testResult.getExceptions(), testResult.getOutput());
            }
        }

        delegate.completed(testDescriptor, delegateResult, completeEvent);

        if (!failed && testResult.getResultType() == TestResult.ResultType.FAILURE) {
            failed = true;
            testExecuter.stopNow();
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        delegate.output(testDescriptor, event);
    }
}
