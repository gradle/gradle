/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestState {
    public final TestDescriptorInternal test;
    final TestStartEvent startEvent;
    private final Map<Object, TestState> executing;
    public boolean failedChild;
    public List<TestFailure> failures = new ArrayList<TestFailure>();
    public long testCount;
    public long successfulCount;
    public long failedCount;
    public TestResult.ResultType resultType;
    TestCompleteEvent completeEvent;

    public TestState(TestDescriptorInternal test, TestStartEvent startEvent, Map<Object, TestState> executing) {
        this.test = test;
        this.startEvent = startEvent;
        this.executing = executing;
    }

    public boolean isFailed() {
        return failedChild || !failures.isEmpty();
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
            TestState parentState = executing.get(startEvent.getParentId());
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
