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
import org.gradle.api.internal.tasks.testing.junit.JUnitTestEventAdapter;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class JUnitPlatformTestExecutionListener extends JUnitTestEventAdapter implements TestExecutionListener {
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        super(resultProcessor, clock, idGenerator);
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
            for (TestIdentifier childIdentifier : currentTestPlan.getChildren(testIdentifier)) {
                testIgnored(getDescriptor(childIdentifier));
            }
        } else if (isMethod(testIdentifier)) {
            testIgnored(getDescriptor(testIdentifier));
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!isMethod(testIdentifier)) {
            return;
        }
        testStarted(testIdentifier.getUniqueId(), getDescriptor(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!isMethod(testIdentifier)) {
            return;
        }
        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL:
                testFinished(testIdentifier.getUniqueId());
                break;
            case FAILED:
                testFailure(testIdentifier.getUniqueId(), getDescriptor(testIdentifier), testExecutionResult.getThrowable().get());
                testFinished(testIdentifier.getUniqueId());
                break;
            case ABORTED:
                testAssumptionFailure(testIdentifier.getUniqueId());
                testFinished(testIdentifier.getUniqueId());
                break;
            default:
                throw new AssertionError("Invalid Status: " + testExecutionResult.getStatus());
        }
    }

    private TestDescriptorInternal getDescriptor(final TestIdentifier test) {
        if (!isMethod(test)) {
            return null;
        } else {
            return new DefaultTestDescriptor(idGenerator.generateId(), className(test.getSource().get()), methodName(test.getSource().get()));
        }
    }

    private String methodName(TestSource testSource) {
        return MethodSource.class.cast(testSource).getMethodName();
    }

    private String className(TestSource testSource) {
        return MethodSource.class.cast(testSource).getClassName();
    }

    private boolean isMethod(TestIdentifier test) {
        if (test.isContainer()) {
            return false;
        }
        return test.getSource().isPresent() && test.getSource().get() instanceof MethodSource;
    }

    private boolean isClass(TestIdentifier test) {
        return test.getSource().isPresent() && test.getSource().get() instanceof ClassSource;
    }
}
