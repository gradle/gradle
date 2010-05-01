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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestResult;

import java.util.HashMap;
import java.util.Map;

public abstract class StateTrackingTestResultProcessor<T extends StateTrackingTestResultProcessor.TestState>
        implements TestResultProcessor {
    private final Map<Object, T> executing = new HashMap<Object, T>();

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        T state = createState(test, event);
        T oldState = executing.put(test.getId(), state);
        if (oldState != null) {
            throw new IllegalArgumentException(String.format("Received a start event for %s with duplicate id '%s'.",
                    test, test.getId()));
        }
        if (event.getParentId() != null) {
            T parentState = executing.get(event.getParentId());
            test.setParent(parentState.test);
        }

        started(state);
    }

    public void completed(Object testId, TestCompleteEvent event) {
        T testState = executing.remove(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format(
                    "Received a completed event for test with unknown id '%s'.", testId));
        }

        testState.completed(event);
        completed(testState);
    }

    public void failure(Object testId, Throwable result) {
        T testState = executing.get(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format("Received a failure event for test with unknown id '%s'.",
                    testId));
        }
        testState.failure = result;
    }

    public void output(Object testId, TestOutputEvent event) {
        // Don't care
    }

    protected void started(T state) {
    }

    protected void completed(T state) {
    }

    protected abstract T createState(TestDescriptorInternal test, TestStartEvent event);

    public class TestState {
        public final TestDescriptorInternal test;
        final TestStartEvent startEvent;
        public boolean failedChild;
        public Throwable failure;
        public long testCount;
        public long successfulCount;
        public long failedCount;
        public TestResult.ResultType resultType;
        TestCompleteEvent completeEvent;

        public TestState(TestDescriptorInternal test, TestStartEvent startEvent) {
            this.test = test;
            this.startEvent = startEvent;
        }

        public boolean isFailed() {
            return failedChild || failure != null;
        }

        public long getStartTime() {
            return startEvent.getStartTime();
        }

        public long getEndTime() {
            return completeEvent.getEndTime();
        }

        public long getExecutionTime() {
            return completeEvent.getEndTime() - startEvent.getStartTime();
        }

        public void completed(TestCompleteEvent event) {
            this.completeEvent = event;
            if (event.getFailure() != null) {
                failure = event.getFailure();
            }
            resultType = isFailed() ? TestResult.ResultType.FAILURE
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
                T parentState = executing.get(startEvent.getParentId());
                if (parentState != null) {
                    if (isFailed()) {
                        parentState.failedChild = true;
                    }
                    parentState.testCount += testCount;
                    parentState.successfulCount += successfulCount;
                    parentState.failedCount += failedCount;
                }
            }
        }
    }
}