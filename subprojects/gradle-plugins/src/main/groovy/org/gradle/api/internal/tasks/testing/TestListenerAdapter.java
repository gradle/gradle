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

import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import java.util.HashMap;
import java.util.Map;

public class TestListenerAdapter implements TestResultProcessor {
    private final TestListener listener;
    private final Map<Object, TestState> executing = new HashMap<Object, TestState>();

    public TestListenerAdapter(TestListener listener) {
        this.listener = listener;
    }

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        TestState oldState = executing.put(test.getId(), new TestState(test, event));
        if (oldState != null) {
            throw new IllegalArgumentException(String.format("Received a start event for %s with duplicate id '%s'.",
                    test, test.getId()));
        }
        if (event.getParentId() != null) {
            TestState parentState = executing.get(event.getParentId());
            test.setParent(parentState.test);
        }
        
        if (test.isComposite()) {
            listener.beforeSuite(test);
        } else {
            listener.beforeTest(test);
        }
    }

    public void completed(Object testId, TestCompleteEvent event) {
        TestState testState = executing.remove(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format(
                    "Received a completed event for test with unknown id '%s'.", testId));
        }
        TestResult result = testState.completed(event);
        TestDescriptorInternal test = testState.test;
        if (test.isComposite()) {
            listener.afterSuite(test, result);
        } else {
            listener.afterTest(test, result);
        }
    }

    public void addFailure(Object testId, Throwable result) {
        TestState testState = executing.get(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format(
                    "Received a failure event for test with unknown id '%s'.", testId));
        }
        testState.failure = result;
    }

    private class TestState {
        final TestDescriptorInternal test;
        final TestStartEvent startEvent;
        boolean failedChild;
        Throwable failure;
        long testCount;
        long successfulCount;
        long failedCount;

        private TestState(TestDescriptorInternal test, TestStartEvent startEvent) {
            this.test = test;
            this.startEvent = startEvent;
        }

        boolean isFailed() {
            return failedChild || failure != null;
        }

        public TestResult completed(TestCompleteEvent event) {
            if (event.getFailure() != null) {
                failure = event.getFailure();
            }
            TestResult.ResultType resultType = isFailed() ? TestResult.ResultType.FAILURE
                    : event.getResultType() != null ? event.getResultType() : TestResult.ResultType.SUCCESS;

            if (!test.isComposite()) {
                testCount = 1;
                switch (resultType) {
                    case SUCCESS:
                        successfulCount = 1;
                        break;
                    case FAILURE:
                        failedCount = 1;
                        break;
                }
            }

            if (startEvent.getParentId() != null) {
                TestState parentState = executing.get(startEvent.getParentId());
                if (isFailed()) {
                    parentState.failedChild = true;
                }
                parentState.testCount += testCount;
                parentState.successfulCount += successfulCount;
                parentState.failedCount += failedCount;
            }

            return new DefaultTestResult(resultType, failure, startEvent.getStartTime(), event.getEndTime(), testCount, successfulCount, failedCount);
        }
    }
}
