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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.time.Clock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Adapt JUnit3/4 and JUnit Platform test event to Gradle test event.
 *
 * @param <T> the test identifier type
 */
public class GenericJUnitTestEventAdapter<T> {
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final Object lock = new Object();
    private final Map<T, TestDescriptorInternal> executing = new HashMap<T, TestDescriptorInternal>();
    private final Set<T> assumptionFailed = new HashSet<T>();

    public GenericJUnitTestEventAdapter(TestResultProcessor resultProcessor, Clock clock) {
        assert resultProcessor instanceof org.gradle.internal.concurrent.ThreadSafe;
        this.resultProcessor = resultProcessor;
        this.clock = clock;
    }

    public void testStarted(T identifier, TestDescriptorInternal descriptor) {
        synchronized (lock) {
            TestDescriptorInternal oldTest = executing.put(identifier, descriptor);
            assert oldTest == null : String.format("Unexpected start event for %s", identifier);
        }
        resultProcessor.started(descriptor, startEvent());
    }

    public void testFailure(T identifier, TestDescriptorInternal descriptor, Throwable exception) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(identifier);
        }
        boolean needEndEvent = false;
        if (testInternal == null) {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            needEndEvent = true;
            testInternal = descriptor;
            resultProcessor.started(testInternal, startEvent());
        }
        resultProcessor.failure(testInternal.getId(), exception);
        if (needEndEvent) {
            resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(clock.getCurrentTime()));
        }
    }

    public void testAssumptionFailure(T identifier) {
        synchronized (lock) {
            assumptionFailed.add(identifier);
        }
    }

    public void testIgnored(TestDescriptorInternal descriptor) {
        resultProcessor.started(descriptor, startEvent());
        resultProcessor.completed(descriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
    }

    public void testFinished(T identifier) {
        long endTime = clock.getCurrentTime();
        TestDescriptorInternal testInternal;
        TestResult.ResultType resultType;
        synchronized (lock) {
            testInternal = executing.remove(identifier);
            if (testInternal == null && executing.size() == 1) {
                // Assume that test has renamed itself (this can actually happen)
                testInternal = executing.values().iterator().next();
                executing.clear();
            }
            assert testInternal != null : String.format("Unexpected end event for %s", identifier);
            resultType = assumptionFailed.remove(identifier) ? TestResult.ResultType.SKIPPED : null;
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime, resultType));
    }

    private TestStartEvent startEvent() {
        return new TestStartEvent(clock.getCurrentTime());
    }
}
