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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

public class TestListenerAdapter extends StateTrackingTestResultProcessor {
    private final TestListener listener;

    public TestListenerAdapter(TestListener listener) {
        this.listener = listener;
    }

    @Override
    protected void started(TestState state) {
        TestDescriptorInternal test = state.test;
        if (test.isComposite()) {
            listener.beforeSuite(test);
        } else {
            listener.beforeTest(test);
        }
    }

    @Override
    protected void completed(TestState state) {
        TestResult result = new DefaultTestResult(state.resultType, state.failures, state.getStartTime(),
                state.getEndTime(), state.testCount, state.successfulCount, state.failedCount);
        TestDescriptorInternal test = state.test;
        if (test.isComposite()) {
            listener.afterSuite(test, result);
        } else {
            listener.afterTest(test, result);
        }
    }
}
