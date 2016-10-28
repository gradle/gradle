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
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.id.IdGenerator;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnitTestEventAdapter extends RunListener {
    private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("(.*)\\((.*)\\)", Pattern.DOTALL);
    private final TestResultProcessor resultProcessor;
    private final TimeProvider timeProvider;
    private final IdGenerator<?> idGenerator;
    private final Object lock = new Object();
    private final Map<Description, TestDescriptorInternal> executing = new HashMap<Description, TestDescriptorInternal>();
    private final Set<Description> assumptionFailed = new HashSet<Description>();

    public JUnitTestEventAdapter(TestResultProcessor resultProcessor, TimeProvider timeProvider,
                                 IdGenerator<?> idGenerator) {
        assert resultProcessor instanceof org.gradle.internal.concurrent.ThreadSafe;
        this.resultProcessor = resultProcessor;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    @Override
    public void testStarted(Description description) throws Exception {
        TestDescriptorInternal descriptor = nullSafeDescriptor(idGenerator.generateId(), description);
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
            testInternal = nullSafeDescriptor(idGenerator.generateId(), failure.getDescription());
            resultProcessor.started(testInternal, startEvent());
        }
        resultProcessor.failure(testInternal.getId(), failure.getException());
        if (needEndEvent) {
            resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime()));
        }
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        synchronized (lock) {
            assumptionFailed.add(failure.getDescription());
        }
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        if (methodName(description) == null) {
            // An @Ignored class, ignore the event. We don't get testIgnored events for each method, so we have
            // generate them on our own
            processIgnoredClass(description);
            return;
        }

        TestDescriptorInternal testInternal = descriptor(idGenerator.generateId(), description);
        resultProcessor.started(testInternal, startEvent());
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(timeProvider.getCurrentTime(), TestResult.ResultType.SKIPPED));
    }

    private void processIgnoredClass(Description description) throws Exception {
        IgnoredTestDescriptorProvider provider = new IgnoredTestDescriptorProvider();
        String className = className(description);
        for (Description childDescription : provider.getAllDescriptions(description, className)) {
            testIgnored(childDescription);
        }
    }

    @Override
    public void testFinished(Description description) throws Exception {
        long endTime = timeProvider.getCurrentTime();
        TestDescriptorInternal testInternal;
        TestResult.ResultType resultType;
        synchronized (lock) {
            testInternal = executing.remove(description);
            if (testInternal == null && executing.size() == 1) {
                // Assume that test has renamed itself (this can actually happen)
                testInternal = executing.values().iterator().next();
                executing.clear();
            }
            assert testInternal != null : String.format("Unexpected end event for %s", description);
            resultType = assumptionFailed.remove(description) ? TestResult.ResultType.SKIPPED : null;
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime, resultType));
    }

    private TestStartEvent startEvent() {
        return new TestStartEvent(timeProvider.getCurrentTime());
    }

    private TestDescriptorInternal descriptor(Object id, Description description) {
        return new DefaultTestDescriptor(id, className(description), methodName(description));
    }

    private TestDescriptorInternal nullSafeDescriptor(Object id, Description description) {
        String methodName = methodName(description);
        if (methodName != null) {
            return new DefaultTestDescriptor(id, className(description), methodName);
        } else {
            return new DefaultTestDescriptor(id, className(description), "classMethod");
        }
    }

    // Use this instead of Description.getMethodName(), it is not available in JUnit <= 4.5
    public static String methodName(Description description) {
        Matcher matcher = methodStringMatcher(description);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // Use this instead of Description.getClassName(), it is not available in JUnit <= 4.5
    public static String className(Description description) {
        Matcher matcher = methodStringMatcher(description);
        return matcher.matches() ? matcher.group(2) : description.toString();
    }

    private static Matcher methodStringMatcher(Description description) {
        return DESCRIPTOR_PATTERN.matcher(description.toString());
    }

}

