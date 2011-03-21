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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.Map;

public class JUnitTestResultProcessorAdapter extends RunListener {
    private final TestResultProcessor resultProcessor;
    private final TimeProvider timeProvider;
    private final IdGenerator<?> idGenerator;
    private final Object lock = new Object();
    private final Map<Description, TestDescriptorInternal> executing = new HashMap<Description, TestDescriptorInternal>();

    public JUnitTestResultProcessorAdapter(TestResultProcessor resultProcessor, TimeProvider timeProvider,
                                 IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        TestDescriptorInternal descriptor = descriptor(idGenerator.generateId(), description);
        synchronized (lock) {
            TestDescriptorInternal oldTest = executing.put(description, descriptor);
            assert oldTest == null : String.format("Unexpected start event for %s", description);
        }
        resultProcessor.started(descriptor, startEvent());
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(failure.getDescription());
        }
        boolean needEndEvent = false;
        if (testInternal == null) {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            needEndEvent = true;
            testInternal = failureDescriptor(idGenerator.generateId(), failure.getDescription());
            resultProcessor.started(testInternal, startEvent());
        }
        resultProcessor.failure(testInternal.getId(), failure.getException());
        if (needEndEvent) {
            resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime()));
        }
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        if (description.getMethodName() == null) {
            // An @Ignored class, ignore the event. We don't get testIgnored events for each method, which would be kind of nice
            return;
        }
        TestDescriptorInternal testInternal = descriptor(idGenerator.generateId(), description);
        resultProcessor.started(testInternal, startEvent());
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime(), TestResult.ResultType.SKIPPED));
    }

    @Override
    public void testFinished(Description description) throws Exception {
        long endTime = timeProvider.getCurrentTime();
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.remove(description);
            if (testInternal == null && executing.size() == 1) {
                // Assume that test has renamed itself
                testInternal = executing.values().iterator().next();
                executing.clear();
            }
            assert testInternal != null : String.format("Unexpected end event for %s", description);
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime));
    }

    private TestStartEvent startEvent() {
        return new TestStartEvent(timeProvider.getCurrentTime());
    }

    private TestDescriptorInternal descriptor(Object id, Description description) {
        return new DefaultTestDescriptor(id, description.getClassName(), description.getMethodName());
    }

    private TestDescriptorInternal failureDescriptor(Object id, Description description) {
        if (description.getMethodName() != null) {
            return new DefaultTestDescriptor(id, description.getClassName(), description.getMethodName());
        } else {
            return new DefaultTestDescriptor(id, description.getClassName(), "classMethod");
        }
    }
}

