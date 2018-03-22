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

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.junit.GenericJUnitTestEventAdapter;
import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import static org.gradle.api.internal.tasks.testing.junitplatform.VintageTestNameAdapter.*;
import static org.junit.platform.engine.TestExecutionResult.Status.SUCCESSFUL;

public class JUnitPlatformTestExecutionListener implements TestExecutionListener {
    private final GenericJUnitTestEventAdapter<String> adapter;
    private final IdGenerator<?> idGenerator;
    private final TestClassExecutionListener executionListener;
    private final CurrentRunningTestClass currentRunningTestClass;
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator, TestClassExecutionListener executionListener) {
        this.adapter = new GenericJUnitTestEventAdapter<>(resultProcessor, clock);
        this.idGenerator = idGenerator;
        this.executionListener = executionListener;
        this.currentRunningTestClass = new CurrentRunningTestClass();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        this.currentTestPlan = null;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (isLeafTest(testIdentifier)) {
            adapter.testIgnored(getDescriptor(testIdentifier));
        } else if (isClass(testIdentifier)) {
            reportTestClassStarted(testIdentifier);
            currentTestPlan.getChildren(testIdentifier).forEach(child -> executionSkipped(child, reason));
            reportTestClassFinished(testIdentifier);
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (isClass(testIdentifier)) {
            reportTestClassStarted(testIdentifier);
        }
        if (isLeafTest(testIdentifier)) {
            adapter.testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
        }
    }

    private boolean isLeafTest(TestIdentifier identifier) {
        return identifier.isTest() && !isVintageDynamicTestClass(identifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testFailedBeforeTestClassStart(testIdentifier, testExecutionResult)) {
            executionFailedBeforeTestClassStart(testIdentifier, testExecutionResult);
        } else {
            executionFinishedAfterTestClassStart(testIdentifier, testExecutionResult);
        }
    }

    private void executionFailedBeforeTestClassStart(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        reportTestClassStarted(testIdentifier);
        reportTestClassFinished(testIdentifier, testExecutionResult.getThrowable().get());
    }

    private void executionFinishedAfterTestClassStart(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        // Generally, there're 2 kinds of identifier:
        // 1. A container (test engine/class/repeated tests). It is not tracked unless it fails/aborts.
        // 2. A test "leaf" method. It's always tracked.
        if (isFailedContainer(testIdentifier, testExecutionResult)) {
            // only leaf methods triggered start events previously
            // so here we need to add the missing start events
            adapter.testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
            testFinished(testIdentifier, testExecutionResult);
        } else if (isLeafTest(testIdentifier)) {
            testFinished(testIdentifier, testExecutionResult);
        }

        if (isClass(testIdentifier)) {
            reportTestClassFinished(testIdentifier);
        }
    }

    private boolean testFailedBeforeTestClassStart(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        return isFailedContainer(testIdentifier, testExecutionResult) && currentRunningTestClass.count == 0;
    }

    private void testFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL:
                break;
            case FAILED:
                adapter.testFailure(testIdentifier.getUniqueId(), getDescriptor(testIdentifier), testExecutionResult.getThrowable().get());
                break;
            case ABORTED:
                adapter.testAssumptionFailure(testIdentifier.getUniqueId());
                break;
            default:
                throw new AssertionError("Invalid Status: " + testExecutionResult.getStatus());
        }
        adapter.testFinished(testIdentifier.getUniqueId());
    }

    private void reportTestClassStarted(TestIdentifier testIdentifier) {
        currentRunningTestClass.start(className(testIdentifier), classDisplayName(testIdentifier));
    }

    private void reportTestClassFinished(TestIdentifier testIdentifier, Throwable failure) {
        currentRunningTestClass.end(className(testIdentifier), failure);
    }

    private void reportTestClassFinished(TestIdentifier testIdentifier) {
        currentRunningTestClass.end(className(testIdentifier), null);
    }

    private boolean isFailedContainer(TestIdentifier testIdentifier, TestExecutionResult result) {
        return result.getStatus() != SUCCESSFUL && testIdentifier.isContainer();
    }

    private TestDescriptorInternal getDescriptor(final TestIdentifier test) {
        if (isMethod(test)) {
            return createDescriptor(test, test.getLegacyReportingName(), test.getDisplayName());
        } else if (isVintageDynamicLeafTest(test)) {
            UniqueId uniqueId = UniqueId.parse(test.getUniqueId());
            return new DefaultTestDescriptor(idGenerator.generateId(), vintageDynamicClassName(uniqueId), vintageDynamicMethodName(uniqueId));
        } else if (isClass(test) || isVintageDynamicTestClass(test)) {
            return createDescriptor(test, "classMethod", "classMethod");
        } else {
            return createDescriptor(test, test.getDisplayName(), test.getDisplayName());
        }
    }

    private TestDescriptorInternal createDescriptor(TestIdentifier test, String name, String displayName) {
        TestIdentifier classIdentifier = findClassSource(test);
        String className = className(classIdentifier);
        String classDisplayName = classDisplayName(classIdentifier);
        return new DefaultTestDescriptor(idGenerator.generateId(), className, name, classDisplayName, displayName);
    }

    private boolean isMethod(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof MethodSource;
    }

    private boolean isClass(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof ClassSource;
    }

    private TestIdentifier findClassSource(TestIdentifier testIdentifier) {
        // For tests in default method of interface,
        // we might not be able to get the implementation class directly.
        // In this case, we need to retrieve test plan to get the real implementation class.
        if (isClass(testIdentifier)) {
            return testIdentifier;
        }
        while (testIdentifier.getParentId().isPresent()) {
            testIdentifier = currentTestPlan.getTestIdentifier(testIdentifier.getParentId().get());
            if (isClass(testIdentifier)) {
                return testIdentifier;
            }
        }
        return null;
    }

    private String className(TestIdentifier testClassIdentifier) {
        if (testClassIdentifier != null && testClassIdentifier.getSource().isPresent()) {
            return ClassSource.class.cast(testClassIdentifier.getSource().get()).getClassName();
        } else {
            return "UnknownClass";
        }
    }

    private String classDisplayName(TestIdentifier testClassIdentifier) {
        if (testClassIdentifier != null) {
            return testClassIdentifier.getDisplayName();
        } else {
            return "UnknownClass";
        }
    }

    private class CurrentRunningTestClass {
        private String name;
        private int count;

        private void start(String className, String displayName) {
            if (name == null) {
                name = className;
                executionListener.testClassStarted(className, displayName);
                count = 1;
            } else if (className.equals(name)) {
                count++;
            }
        }

        private void end(String className, Throwable failure) {
            if (className.equals(name)) {
                count--;
                if (count == 0) {
                    executionListener.testClassFinished(failure);
                    name = null;
                }
            }
        }
    }
}
