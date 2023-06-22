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

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JUnitTestEventAdapter extends RunListener {
    private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("(.*)\\((.*)\\)(\\[\\d+])?", Pattern.DOTALL);
    private final IdGenerator<?> idGenerator;
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final Object lock = new Object();
    private final Map<Description, TestDescriptorInternal> executing = new HashMap<Description, TestDescriptorInternal>();
    private final Set<Description> assumptionFailed = new HashSet<Description>();

    public JUnitTestEventAdapter(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void testStarted(Description description) {
        TestDescriptorInternal descriptor = nullSafeDescriptor(idGenerator.generateId(), description);
        synchronized (lock) {
            TestDescriptorInternal oldTest = executing.put(description, descriptor);
            assert oldTest == null : String.format("Unexpected start event for %s", description);
        }
        resultProcessor.started(descriptor, startEvent());
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        TestDescriptorInternal descriptor = nullSafeDescriptor(idGenerator.generateId(), failure.getDescription());
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(failure.getDescription());
        }
        boolean needEndEvent = false;
        if (testInternal == null) {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            needEndEvent = true;
            testInternal = descriptor;
            resultProcessor.started(testInternal, startEvent());
        }

        Throwable exception = failure.getException();
        reportFailure(testInternal.getId(), exception);
        if (needEndEvent) {
            resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(clock.getCurrentTime()));
        }
    }

    private TestFailure createFailure(Throwable failure) throws Exception {
        // According to https://junit.org/junit4/javadoc/latest/overview-tree.html, JUnit assertion failures can be expressed with the following exceptions:
        // - java.lang.AssertionError: general assertion errors, i.e. test code contains assert statements
        // - org.junit.ComparisonFailure: when assertEquals (and similar assertion) fails; test code can throw it directly
        // - junit.framework.ComparisonFailure: for older JUnit tests using JUnit 3.x fixtures
        // All assertion errors are subclasses of the AssertionError class. If the received failure is not an instance of AssertionError then it is categorized as a framework failure.
//        if (failure instanceof ComparisonFailure) {
//            ComparisonFailure comparisonFailure = (ComparisonFailure) failure;
//            return TestFailure.fromTestAssertionFailure(failure, comparisonFailure.getExpected(), comparisonFailure.getActual());
//        } else if (failure instanceof junit.framework.ComparisonFailure) {
//            junit.framework.ComparisonFailure comparisonFailure = (junit.framework.ComparisonFailure) failure;
//            return TestFailure.fromTestAssertionFailure(failure, getValueOfStringField("fExpected", comparisonFailure), getValueOfStringField("fActual", comparisonFailure));
//        } else if (failure instanceof AssertionError) {
//            if (OpentestMultipleFailuresMapper.accepts(failure.getClass())) {
//                List<TestFailure> innerFailures = new ArrayList<TestFailure>();
//                for(Throwable innerCause: OpentestMultipleFailuresMapper.getInnerFailures(failure)) {
//                    innerFailures.add(createFailure(innerCause));
//                }
//                return OpentestMultipleFailuresMapper.map(failure, innerFailures);
//            } else if (OpentestFailureFailedMapper.accepts(failure.getClass())) {
//                return OpentestFailureFailedMapper.map(failure, null);
//            } else {
//                return TestFailure.fromTestFrameworkFailure(failure);
//            }
//        } else {
            return TestFailure.fromTestFrameworkFailure(failure);
//        }
    }

    private void reportFailure(Object descriptorId, Throwable failure) throws Exception {
        resultProcessor.failure(descriptorId, createFailure(failure));
    }

    private String getValueOfStringField(String name, junit.framework.ComparisonFailure comparisonFailure) {
        try {
            Field f = comparisonFailure.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (String) f.get(comparisonFailure);
        } catch (Exception e) {
            return null;
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
        } else {
            TestDescriptorInternal descriptor = descriptor(idGenerator.generateId(), description);
            resultProcessor.started(descriptor, startEvent());
            resultProcessor.completed(descriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
        }
    }

    private void processIgnoredClass(Description description) throws Exception {
        String className = className(description);
        for (Description childDescription : IgnoredTestDescriptorProvider.getAllDescriptions(description, className)) {
            testIgnored(childDescription);
        }
    }

    @Override
    public void testFinished(Description description) {
        long endTime = clock.getCurrentTime();
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
        return methodName(description.toString());
    }

    public static String methodName(String description) {
        Matcher matcher = methodStringMatcher(description);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // Use this instead of Description.getClassName(), it is not available in JUnit <= 4.5
    public static String className(Description description) {
        return className(description.toString());
    }

    public static String className(String description) {
        Matcher matcher = methodStringMatcher(description);
        return matcher.matches() ? matcher.group(2) : description;
    }

    private static Matcher methodStringMatcher(String description) {
        return DESCRIPTOR_PATTERN.matcher(description);
    }

    private TestStartEvent startEvent() {
        return new TestStartEvent(clock.getCurrentTime());
    }

}

