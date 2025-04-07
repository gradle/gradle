/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;

@NonNullApi
public class TestRetryShieldingTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor delegate;

    public TestRetryShieldingTestResultProcessor(TestResultProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(TestDescriptorInternal test, TestStartEvent event) {
        delegate.started(test, event);
    }

    @Override
    public void completed(Object testId, TestCompleteEvent event) {
        delegate.completed(testId, event);
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        delegate.output(testId, event);
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        if (result.getDetails().isAssumptionFailure()) {
            // Don't report assumption failures to the delegate because test retry does not understand them.
            return;
        }
        delegate.failure(testId, result);
    }
}
