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
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.util.HashMap;
import java.util.Map;

public abstract class StateTrackingTestResultProcessor implements TestResultProcessor {
    private final Map<Object, TestState> executing = new HashMap<Object, TestState>();

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        TestDescriptorInternal parent = null;
        if (event.getParentId() != null) {
            parent = executing.get(event.getParentId()).test;
        }
        TestState state = new TestState(new DecoratingTestDescriptor(test, parent), event, executing);
        TestState oldState = executing.put(test.getId(), state);
        if (oldState != null) {
            throw new IllegalArgumentException(String.format("Received a start event for %s with duplicate id '%s'.",
                    test, test.getId()));
        }

        started(state);
    }

    public void completed(Object testId, TestCompleteEvent event) {
        TestState testState = executing.remove(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format(
                    "Received a completed event for test with unknown id '%s'. Registered test ids: '%s'",
                    testId, executing.keySet()));
        }

        testState.completed(event);
        completed(testState);
    }

    public void failure(Object testId, Throwable result) {
        TestState testState = executing.get(testId);
        if (testState == null) {
            throw new IllegalArgumentException(String.format(
                    "Received a failure event for test with unknown id '%s'. Registered test ids: '%s'",
                    testId, executing.keySet()));
        }
        testState.failures.add(result);
    }

    public void output(Object testId, TestOutputEvent event) {
        // Don't care
    }

    protected void started(TestState state) {
    }

    protected void completed(TestState state) {
    }

    protected TestState getTestStateFor(Object testId) {
        return executing.get(testId);
    }
}