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

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestContainerDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestResult.ResultType;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;
import static org.junit.platform.engine.TestExecutionResult.Status.ABORTED;
import static org.junit.platform.engine.TestExecutionResult.Status.FAILED;
import static org.junit.platform.launcher.listeners.LegacyReportingUtils.getClassName;

public class JUnitPlatformTestExecutionListener implements TestExecutionListener {
    private final TestResultProcessor resultProcessor;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final Map<String, TestDescriptorInternal> descriptors = new ConcurrentHashMap<>();
    private TestPlan currentTestPlan;

    JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.idGenerator = idGenerator;
        this.clock = clock;
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
        resultProcessor.started(getDescriptor(testIdentifier), startEvent(testIdentifier));
        reportSkipped(testIdentifier, reason);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        resultProcessor.started(getDescriptor(testIdentifier), startEvent(testIdentifier));
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testExecutionResult.getStatus() == ABORTED) {
            reportSkipped(testIdentifier, testExecutionResult.getThrowable().map(Throwable::getMessage).orElse(""));
            return;
        }
        if (testExecutionResult.getStatus() == FAILED) {
            resultProcessor.failure(getId(testIdentifier), testExecutionResult.getThrowable().orElse(null));
        }
        resultProcessor.completed(getId(testIdentifier), completeEvent(null));
        descriptors.remove(testIdentifier.getUniqueId());
    }

    private void reportSkipped(TestIdentifier testIdentifier, String reason) {
        ResultType resultType = null;
        if (isLeafTest(testIdentifier)) {
            resultType = SKIPPED;
        } else {
            currentTestPlan.getChildren(testIdentifier).forEach(child -> executionSkipped(child, reason));
        }
        resultProcessor.completed(getId(testIdentifier), completeEvent(resultType));
    }

    private TestCompleteEvent completeEvent(ResultType resultType) {
        return new TestCompleteEvent(clock.getCurrentTime(), resultType);
    }

    private TestStartEvent startEvent(TestIdentifier testIdentifier) {
        Object parentId = testIdentifier.getParentId().map(descriptors::get).map(TestDescriptorInternal::getId).orElse(null);
        return new TestStartEvent(clock.getCurrentTime(), parentId);
    }

    private boolean isLeafTest(TestIdentifier identifier) {
        return currentTestPlan.getChildren(identifier).isEmpty();
    }

    private TestDescriptorInternal getDescriptor(TestIdentifier node) {
        return descriptors.computeIfAbsent(node.getUniqueId(), uniqueId -> {
            Optional<TestIdentifier> parent = currentTestPlan.getParent(node);
            Object id = idGenerator.generateId();
            String name = node.getLegacyReportingName();
            String displayName = node.getDisplayName();
            if (!parent.isPresent()) {
                return new DefaultTestSuiteDescriptor(id, name, displayName);
            }
            Optional<DefaultTestClassDescriptor> testClassDescriptor = findTestClassDescriptor(parent.get());
            if (testClassDescriptor.isPresent()) {
                String className = testClassDescriptor.get().getClassName();
                String classDisplayName = testClassDescriptor.get().getClassDisplayName();
                if (node.isContainer()) {
                    return new DefaultTestContainerDescriptor(id, className, name, classDisplayName, displayName);
                }
                return new DefaultTestDescriptor(id, className, name, classDisplayName, displayName);
            }
            if (node.isContainer()) {
                return new DefaultTestClassDescriptor(id, name, displayName);
            }
            return new DefaultTestDescriptor(id, getClassName(currentTestPlan, node), name, parent.get().getDisplayName(), displayName);
        });
    }

    private Optional<DefaultTestClassDescriptor> findTestClassDescriptor(TestIdentifier testIdentifier) {
        TestDescriptorInternal descriptor = getDescriptor(testIdentifier);
        if (descriptor instanceof DefaultTestClassDescriptor) {
            return Optional.of((DefaultTestClassDescriptor) descriptor);
        }
        return currentTestPlan.getParent(testIdentifier).flatMap(this::findTestClassDescriptor);
    }

    private Object getId(TestIdentifier testIdentifier) {
        return getDescriptor(testIdentifier).getId();
    }
}
