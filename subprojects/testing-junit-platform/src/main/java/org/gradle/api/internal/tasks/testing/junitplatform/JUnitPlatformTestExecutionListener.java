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
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class JUnitPlatformTestExecutionListener implements TestExecutionListener {
    private final GenericJUnitTestEventAdapter<String> adapter;
    private final IdGenerator<?> idGenerator;
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.adapter = new GenericJUnitTestEventAdapter<>(resultProcessor, clock);
        this.idGenerator = idGenerator;
    }

    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
    }

    public void testPlanExecutionFinished(TestPlan testPlan) {
        this.currentTestPlan = null;
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (isClass(testIdentifier)) {
            currentTestPlan.getChildren(testIdentifier).forEach(child -> executionSkipped(child, reason));
        } else if (isMethod(testIdentifier)) {
            adapter.testIgnored(getDescriptor(testIdentifier));
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!isLeafMethod(testIdentifier)) {
            return;
        }
        adapter.testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (isLeafMethodOrFailedContainer(testIdentifier, testExecutionResult)) {
            switch (testExecutionResult.getStatus()) {
                case SUCCESSFUL:
                    adapter.testFinished(testIdentifier.getUniqueId());
                    break;
                case FAILED:
                    adapter.testFailure(testIdentifier.getUniqueId(), getDescriptor(testIdentifier), testExecutionResult.getThrowable().get());
                    if (isLeafMethod(testIdentifier)) {
                        // only leaf methods needs finish event because they triggered start event previously
                        adapter.testFinished(testIdentifier.getUniqueId());
                    }
                    break;
                case ABORTED:
                    adapter.testAssumptionFailure(testIdentifier.getUniqueId());
                    adapter.testFinished(testIdentifier.getUniqueId());
                    break;
                default:
                    throw new AssertionError("Invalid Status: " + testExecutionResult.getStatus());
            }
        }
    }

    private boolean isLeafMethodOrFailedContainer(TestIdentifier testIdentifier, TestExecutionResult result) {
        // Generally, there're 4 kinds of identifier:
        // 1. JUnit test engine, which we don't consider at all
        // 2. A container (class or repeated tests). It is not tracked unless it fails.
        // 3. A test "leaf" method. It's always tracked.
        return isLeafMethod(testIdentifier) || isFailedContainer(testIdentifier, result);
    }

    private boolean isFailedContainer(TestIdentifier testIdentifier, TestExecutionResult result) {
        return result.getStatus() != TestExecutionResult.Status.SUCCESSFUL && testIdentifier.isContainer();
    }

    private TestDescriptorInternal getDescriptor(final TestIdentifier test) {
        if (isMethod(test)) {
            String className = MethodSource.class.cast(test.getSource().get()).getClassName();
            return new DefaultTestDescriptor(idGenerator.generateId(), className, test.getDisplayName());
        } else if (isClass(test)) {
            String className = ClassSource.class.cast(test.getSource().get()).getClassName();
            return new DefaultTestDescriptor(idGenerator.generateId(), className, "classMethod");
        } else {
            return null;
        }
    }

    private boolean isMethod(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof MethodSource;
    }

    private boolean isLeafMethod(TestIdentifier test) {
        // e.g. an iteration in a method annotated with @RepeatedTest
        return isMethod(test) && test.getType() == TestDescriptor.Type.TEST;
    }

    private boolean isClass(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof ClassSource;
    }
}
