/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractTestResult;
import org.gradle.internal.build.event.types.DefaultFileComparisonTestAssertionFailure;
import org.gradle.internal.build.event.types.DefaultTestAssertionFailure;
import org.gradle.internal.build.event.types.DefaultTestDescriptor;
import org.gradle.internal.build.event.types.DefaultTestFailureResult;
import org.gradle.internal.build.event.types.DefaultTestFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTestFrameworkFailure;
import org.gradle.internal.build.event.types.DefaultTestSkippedResult;
import org.gradle.internal.build.event.types.DefaultTestStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTestSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

class TestOperationMapper implements BuildOperationMapper<ExecuteTestBuildOperationType.Details, DefaultTestDescriptor> {
    private final TestTaskExecutionTracker taskTracker;

    TestOperationMapper(TestTaskExecutionTracker taskTracker) {
        this.taskTracker = taskTracker;
    }

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.TEST);
    }

    @Override
    public Class<ExecuteTestBuildOperationType.Details> getDetailsType() {
        return ExecuteTestBuildOperationType.Details.class;
    }

    @Override
    public List<? extends BuildOperationTracker> getTrackers() {
        return ImmutableList.of(taskTracker);
    }

    @Override
    public DefaultTestDescriptor createDescriptor(ExecuteTestBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        TestDescriptorInternal testDescriptor = (TestDescriptorInternal) details.getTestDescriptor();
        return testDescriptor.isComposite() ? toTestDescriptorForSuite(buildOperation.getId(), parent, testDescriptor) : toTestDescriptorForTest(buildOperation.getId(), parent, testDescriptor);
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultTestDescriptor descriptor, ExecuteTestBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultTestStartedProgressEvent(details.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultTestDescriptor descriptor, ExecuteTestBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        TestResult testResult = ((ExecuteTestBuildOperationType.Result) finishEvent.getResult()).getResult();
        return new DefaultTestFinishedProgressEvent(testResult.getEndTime(), descriptor, adapt(testResult));
    }

    private DefaultTestDescriptor toTestDescriptorForSuite(OperationIdentifier buildOperationId, OperationIdentifier parentId, TestDescriptorInternal suite) {
        String name = suite.getName();
        String displayName = backwardsCompatibleDisplayNameOf(suite);
        String testKind = InternalJvmTestDescriptor.KIND_SUITE;
        String className = suite.getClassName();
        String methodName = null;
        String testTaskPath = taskTracker.getTaskPath(buildOperationId);
        return new DefaultTestDescriptor(buildOperationId, name, displayName, testKind, suite.getName(), className, methodName, parentId, testTaskPath);
    }

    private DefaultTestDescriptor toTestDescriptorForTest(OperationIdentifier buildOperationId, OperationIdentifier parentId, TestDescriptorInternal test) {
        String name = test.getName();
        String displayName = backwardsCompatibleDisplayNameOf(test);
        String testKind = InternalJvmTestDescriptor.KIND_ATOMIC;
        String className = test.getClassName();
        String methodName = test.getName();
        String taskPath = taskTracker.getTaskPath(buildOperationId);
        return new DefaultTestDescriptor(buildOperationId, name, displayName, testKind, null, className, methodName, parentId, taskPath);
    }

    /**
     * This method returns a display name which is "compatible with" what previous
     * Gradle versions did.
     */
    private static String backwardsCompatibleDisplayNameOf(TestDescriptorInternal descriptor) {
        String className = descriptor.getClassName();
        String methodName = descriptor.getName();
        String displayName = descriptor.getDisplayName();
        if (methodName != null && methodName.equals(displayName) || className != null && className.equals(displayName)) {
            return descriptor.toString();
        }
        return displayName;
    }

    private static AbstractTestResult adapt(TestResult result) {
        TestResult.ResultType resultType = result.getResultType();
        switch (resultType) {
            case SUCCESS:
                return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
            case SKIPPED:
                return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
            case FAILURE:
                return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), convertExceptions(result.getFailures()));
            default:
                throw new IllegalStateException("Unknown test result type: " + resultType);
        }
    }

    private static List<InternalFailure> convertExceptions(List<TestFailure> failures) {
        List<InternalFailure> result = new ArrayList<>(failures.size());
        for (TestFailure failure : failures) {
            if (failure.getDetails().isAssertionFailure()) {
                if (failure.getDetails().isFileComparisonFailure()) {
                    result.add(DefaultFileComparisonTestAssertionFailure.create(
                        failure.getRawFailure(),
                        failure.getDetails().getMessage(),
                        failure.getDetails().getClassName(),
                        failure.getDetails().getStacktrace(),
                        failure.getDetails().getExpected(),
                        failure.getDetails().getActual(),
                        convertExceptions(failure.getCauses()),
                        failure.getDetails().getExpectedContent(),
                        failure.getDetails().getActualContent()
                    ));
                } else {
                    result.add(DefaultTestAssertionFailure.create(
                        failure.getRawFailure(),
                        failure.getDetails().getMessage(),
                        failure.getDetails().getClassName(),
                        failure.getDetails().getStacktrace(),
                        failure.getDetails().getExpected(),
                        failure.getDetails().getActual(),
                        convertExceptions(failure.getCauses())
                    ));
                }
            } else {
                result.add(DefaultTestFrameworkFailure.create(
                    failure.getRawFailure(),
                    failure.getDetails().getMessage(),
                    failure.getDetails().getClassName(),
                    failure.getDetails().getStacktrace()
                ));
            }
        }
        return result;
    }
}
