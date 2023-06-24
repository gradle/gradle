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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.DefaultNestedTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.junit.JUnitSupport;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult.ResultType;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;
import static org.junit.platform.engine.TestExecutionResult.Status.ABORTED;
import static org.junit.platform.engine.TestExecutionResult.Status.FAILED;

public class JUnitPlatformTestExecutionListener implements TestExecutionListener {

    private final ConcurrentMap<String, TestDescriptorInternal> descriptorsByUniqueId = new ConcurrentHashMap<>();
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final IdGenerator<?> idGenerator;
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        this.currentTestPlan = null;
        this.descriptorsByUniqueId.clear();
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        executionSkipped(testIdentifier);
    }

    private void executionSkipped(TestIdentifier testIdentifier) {
        executionStarted(testIdentifier);
        reportSkipped(testIdentifier);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        // The root node will be "JUnit Jupiter" which isn't expected
        // to be seen as a "real" test suite in many tests, so this
        // test is to make sure we're at least under this event
        if (testIdentifier.getParentId().isPresent()) {
            reportStartedUnlessAlreadyStarted(testIdentifier);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testExecutionResult.getStatus() == ABORTED) {
            reportSkipped(testIdentifier);
            return;
        }
        if (testExecutionResult.getStatus() == FAILED) {
            reportStartedUnlessAlreadyStarted(testIdentifier);
            Throwable failure = testExecutionResult.getThrowable().orElseGet(() -> new AssertionError("test failed but did not report an exception"));
            if (testIdentifier.isTest()) {
                reportTestFailure(testIdentifier, failure);
            } else {
                TestDescriptorInternal syntheticTestDescriptor = createSyntheticTestDescriptorForContainer(testIdentifier);
                resultProcessor.started(syntheticTestDescriptor, startEvent(getId(testIdentifier)));
                resultProcessor.failure(syntheticTestDescriptor.getId(), TestFailure.fromTestFrameworkFailure(failure));
                resultProcessor.completed(syntheticTestDescriptor.getId(), completeEvent());
            }
        }
        if (wasStarted(testIdentifier)) {
            resultProcessor.completed(getId(testIdentifier), completeEvent());
        }
    }

    private void reportTestFailure(TestIdentifier testIdentifier, Throwable failure) {
        TestFailure testFailure = createFailure(failure);
        resultProcessor.failure(getId(testIdentifier), testFailure);
    }

    private static TestFailure createFailure(Throwable failure) {
        // According to https://ota4j-team.github.io/opentest4j/docs/current/api/overview-tree.html, JUnit assertion failures can be expressed with the following exceptions:
        // - java.lang.AssertionError: general assertion errors, i.e. test code contains assert statements
        // - org.opentest4j.AssertionFailedError: when an assertEquals fails
        // - org.opentest4j.MultipleFailuresError: when multiple assertion fails at the same time
        // All assertion errors are subclasses of the AssertionError class. If the received failure is not an instance of AssertionError then it is categorized as a framework failure.
        // Also, openTest4j classes are not on the worker compile classpath so we need to resort to using reflection.
        if (failure instanceof AssertionError) {
            List<Throwable> causes = getFailureListFromMultipleFailuresError(failure);
            List<TestFailure> causeFailures = causes == null ? Collections.emptyList() : causes.stream().map(f -> createFailure(f)).collect(Collectors.toList());
            String expected = reflectivelyReadExpected(failure);
            String actual = reflectivelyReadActual(failure);
            return TestFailure.fromTestAssertionFailure(failure, expected, actual, causeFailures);
        } else {
            return TestFailure.fromTestFrameworkFailure(failure);
        }
    }

    private static String reflectivelyReadExpected(Throwable failure) {
        return reflectivelyRead(failure, "getExpected");
    }

    private static String reflectivelyReadActual(Throwable failure) {
        return reflectivelyRead(failure, "getActual");
    }

    private static String reflectivelyRead(Object target, String methodName) {
        String toStringMethod = isAssertionFailedErrorOrSubclass(target.getClass()) ? "getStringRepresentation" : "toString";
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : (String) value.getClass().getMethod(toStringMethod).invoke(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Throwable> getFailureListFromMultipleFailuresError(Throwable f) {
        try {
            String className = f.getClass().getCanonicalName();
            if (className.equals("org.opentest4j.MultipleFailuresError")) {
                Method getFailures = f.getClass().getMethod("getFailures");
                return (List<Throwable>) getFailures.invoke(f);
            } else if (className.equals("org.assertj.core.error.MultipleAssertionsError")) {
                Method getFailures = f.getClass().getMethod("getErrors");
                return (List<Throwable>) getFailures.invoke(f);
            } else {
                return null;
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    // if not multiple failures or reflection fails then return null;

    private static boolean isAssertionFailedErrorOrSubclass(Class<?> cls) {
        if (cls.getCanonicalName().equals("org.opentest4j.AssertionFailedError")) {
            return true;
        } else if (cls.getSuperclass() != null) {
            return isAssertionFailedErrorOrSubclass(cls.getSuperclass());
        } else {
            return false;
        }
    }

    private void reportStartedUnlessAlreadyStarted(TestIdentifier testIdentifier) {
        boolean wasNotAlreadyStarted = createDescriptorIfAbsent(testIdentifier);
        // guard against edge cases (e.g. JUnit 4 classes with custom runners that report the class as ignored after reporting it as started)
        if (wasNotAlreadyStarted) {
            TestDescriptorInternal descriptor = descriptorsByUniqueId.get(testIdentifier.getUniqueId());
            resultProcessor.started(descriptor, startEvent(testIdentifier));
        }
    }

    private void reportSkipped(TestIdentifier testIdentifier) {
        currentTestPlan.getChildren(testIdentifier).stream()
            .filter(child -> !wasStarted(child))
            .forEach(this::executionSkipped);
        if (testIdentifier.isTest()) {
            resultProcessor.completed(getId(testIdentifier), completeEvent(SKIPPED));
        } else if (hasClassSource(testIdentifier)) {
            resultProcessor.completed(getId(testIdentifier), completeEvent());
        }
    }

    private TestStartEvent startEvent(TestIdentifier testIdentifier) {
        Object idOfClosestStartedAncestor = getAncestors(testIdentifier).stream()
            .map(TestIdentifier::getUniqueId)
            .filter(descriptorsByUniqueId::containsKey)
            .findFirst()
            .map(descriptorsByUniqueId::get)
            .map(TestDescriptorInternal::getId)
            .orElse(null);
        return startEvent(idOfClosestStartedAncestor);
    }

    private TestStartEvent startEvent(Object parentId) {
        return new TestStartEvent(clock.getCurrentTime(), parentId);
    }

    private TestCompleteEvent completeEvent() {
        return completeEvent(null);
    }

    private TestCompleteEvent completeEvent(ResultType resultType) {
        return new TestCompleteEvent(clock.getCurrentTime(), resultType);
    }

    private boolean wasStarted(TestIdentifier testIdentifier) {
        return descriptorsByUniqueId.containsKey(testIdentifier.getUniqueId());
}

    private boolean createDescriptorIfAbsent(TestIdentifier node) {
        MutableBoolean wasCreated = new MutableBoolean(false);
        descriptorsByUniqueId.computeIfAbsent(node.getUniqueId(), uniqueId -> {
            wasCreated.set(true);
            boolean isTestClassId = isTestClassIdentifier(node);
            if (node.getType().isContainer() || isTestClassId) {
                if (isTestClassId) {
                    return createTestClassDescriptor(node);
                }
                String displayName = node.getDisplayName();
                Optional<TestDescriptorInternal> parentId = node.getParentId().map(descriptorsByUniqueId::get);
                if (parentId.isPresent()) {
                    Object candidateId = parentId.get().getId();
                    if (candidateId instanceof CompositeIdGenerator.CompositeId) {
                        return createNestedTestSuite(node, displayName, (CompositeIdGenerator.CompositeId) candidateId);
                    }
                }
            }
            if (node.getType().isTest()) {
                return createTestDescriptor(node, node.getLegacyReportingName(), node.getDisplayName());
            } else {
                return createTestClassDescriptor(node);
            }
        });
        return wasCreated.get();
    }

    private DefaultNestedTestSuiteDescriptor createNestedTestSuite(TestIdentifier node, String displayName, CompositeIdGenerator.CompositeId candidateId) {
        return new DefaultNestedTestSuiteDescriptor(idGenerator.generateId(), node.getLegacyReportingName(), displayName, candidateId);
    }

    private DefaultTestClassDescriptor createTestClassDescriptor(TestIdentifier node) {
        TestIdentifier classIdentifier = findTestClassIdentifier(node);
        String className = className(classIdentifier);
        String classDisplayName = node.getDisplayName();
        return new DefaultTestClassDescriptor(idGenerator.generateId(), className, classDisplayName);
    }

    private TestDescriptorInternal createSyntheticTestDescriptorForContainer(TestIdentifier node) {
        boolean testsStarted = currentTestPlan.getDescendants(node).stream().anyMatch(this::wasStarted);
        String name = testsStarted ? "executionError" : "initializationError";
        return createTestDescriptor(node, name, name);
    }

    private TestDescriptorInternal createTestDescriptor(TestIdentifier test, String name, String displayName) {
        TestIdentifier classIdentifier = findTestClassIdentifier(test);
        String className = className(classIdentifier);
        String classDisplayName = classDisplayName(classIdentifier);
        return new DefaultTestDescriptor(idGenerator.generateId(), className, name, classDisplayName, displayName);
    }

    private Object getId(TestIdentifier testIdentifier) {
        return descriptorsByUniqueId.get(testIdentifier.getUniqueId()).getId();
    }

    private Set<TestIdentifier> getAncestors(TestIdentifier testIdentifier) {
        Set<TestIdentifier> result = new LinkedHashSet<>();
        Optional<String> parentId = testIdentifier.getParentId();
        while (parentId.isPresent()) {
            TestIdentifier parent = currentTestPlan.getTestIdentifier(parentId.get());
            result.add(parent);
            parentId = parent.getParentId();
        }
        return result;
    }

    private TestIdentifier findTestClassIdentifier(TestIdentifier testIdentifier) {
        // For tests in default method of interface,
        // we might not be able to get the implementation class directly.
        // In this case, we need to retrieve test plan to get the real implementation class.
        TestIdentifier current = testIdentifier;
        while (current != null) {
            if (isTestClassIdentifier(current)) {
                return current;
            }
            current = current.getParentId().map(currentTestPlan::getTestIdentifier).orElse(null);
        }
        return null;
    }

    private boolean isTestClassIdentifier(TestIdentifier testIdentifier) {
        return hasClassSource(testIdentifier) && hasDifferentSourceThanAncestor(testIdentifier);
    }

    private String className(TestIdentifier testClassIdentifier) {
        if (testClassIdentifier != null) {
            Optional<ClassSource> classSource = getClassSource(testClassIdentifier);
            if (classSource.isPresent()) {
                return classSource.get().getClassName();
            }
        }
        return JUnitSupport.UNKNOWN_CLASS;
    }

    private String classDisplayName(TestIdentifier testClassIdentifier) {
        if (testClassIdentifier != null) {
            return testClassIdentifier.getDisplayName();
        }
        return JUnitSupport.UNKNOWN_CLASS;
    }

    private static boolean hasClassSource(TestIdentifier testIdentifier) {
        return getClassSource(testIdentifier).isPresent();
    }

    private static Optional<ClassSource> getClassSource(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
            .filter(source -> source instanceof ClassSource)
            .map(source -> (ClassSource) source);
    }

    private boolean hasDifferentSourceThanAncestor(TestIdentifier testIdentifier) {
        Optional<TestIdentifier> parent = currentTestPlan.getParent(testIdentifier);
        while (parent.isPresent()) {
            if (Objects.equals(parent.get().getSource(), testIdentifier.getSource())) {
                return false;
            }
            parent = currentTestPlan.getParent(parent.get());
        }
        return true;
    }

}
