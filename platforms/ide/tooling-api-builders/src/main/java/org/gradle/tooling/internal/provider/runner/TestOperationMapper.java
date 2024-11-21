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
import org.gradle.api.internal.tasks.testing.AbstractTestDescriptor;
import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultParameterizedTestDescriptor;
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.api.tasks.testing.TestDescriptor;
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
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

class TestOperationMapper implements BuildOperationMapper<ExecuteTestBuildOperationType.Details, DefaultTestDescriptor> {
    private final TaskForTestEventTracker taskTracker;

    TestOperationMapper(TaskForTestEventTracker taskTracker) {
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
        TestDescriptor testDescriptor = details.getTestDescriptor();
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

    private DefaultTestDescriptor toTestDescriptorForSuite(OperationIdentifier buildOperationId, OperationIdentifier parentId, TestDescriptor suite) {
        String methodName = null;
        String operationDisplayName = suite.toString();

        TestDescriptor originalDescriptor = getOriginalDescriptor(suite);
        if (originalDescriptor instanceof AbstractTestDescriptor) {
            methodName = ((AbstractTestDescriptor) originalDescriptor).getMethodName();
            operationDisplayName = adjustOperationDisplayNameForIntelliJ(operationDisplayName, (AbstractTestDescriptor) originalDescriptor);
        } else {
            operationDisplayName = getLegacyOperationDisplayName(operationDisplayName, originalDescriptor);
        }
        return new DefaultTestDescriptor(buildOperationId, suite.getName(), operationDisplayName, suite.getDisplayName(), InternalJvmTestDescriptor.KIND_SUITE, suite.getName(), suite.getClassName(), methodName, parentId, taskTracker.getTaskPath(buildOperationId));
    }

    private DefaultTestDescriptor toTestDescriptorForTest(OperationIdentifier buildOperationId, OperationIdentifier parentId, TestDescriptor test) {
        String operationDisplayName = test.toString();

        TestDescriptor originalDescriptor = getOriginalDescriptor(test);
        if (originalDescriptor instanceof AbstractTestDescriptor) {
            operationDisplayName = adjustOperationDisplayNameForIntelliJ(operationDisplayName, (AbstractTestDescriptor) originalDescriptor);
        } else {
            operationDisplayName = getLegacyOperationDisplayName(operationDisplayName, originalDescriptor);
        }
        return new DefaultTestDescriptor(buildOperationId, test.getName(), operationDisplayName, test.getDisplayName(), InternalJvmTestDescriptor.KIND_ATOMIC, null, test.getClassName(), test.getName(), parentId, taskTracker.getTaskPath(buildOperationId));
    }

    /**
     * This is a workaround to preserve backward compatibility with IntelliJ IDEA.
     * The problem only occurs in IntelliJ IDEA because it parses {@link OperationDescriptor#getDisplayName()} to get the test display name.
     * Once its code is updated to use {@link org.gradle.tooling.events.test.TestOperationDescriptor#getTestDisplayName()}, the workaround can be removed as well.
     * Alternatively, it can be removed in Gradle 9.0.
     * See <a href="https://github.com/gradle/gradle/issues/24538">this issue</a> for more details.
     */
    private String adjustOperationDisplayNameForIntelliJ(String operationDisplayName, AbstractTestDescriptor descriptor) {
        String displayName = descriptor.getDisplayName();
        if (!descriptor.getName().equals(displayName) && !(descriptor.getClassDisplayName() != null && descriptor.getName().endsWith(descriptor.getClassDisplayName()))) {
            return descriptor.getDisplayName();
        } else if (descriptor instanceof DefaultParameterizedTestDescriptor) { // for spock parameterized tests
            return descriptor.getDisplayName();
        }
        return operationDisplayName;
    }

    /**
     * This is a workaround for Kotlin Gradle Plugin <a href="https://github.com/JetBrains/kotlin/blob/1d38040a6bef2dba31d447bf28c220b81665a710/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/internal/MppTestReportHelper.kt#L55-L64">overriding TestDescriptor</a>.
     * The problem only occurs in IntelliJ IDEA with multiplatform projects.
     * Once this code is removed, the workaround can be removed as well and {@link org.gradle.api.internal.tasks.testing.AbstractTestDescriptor#getMethodName()} can be moved to {@link TestDescriptor}.
     * Alternatively, it can be removed in Gradle 9.0.
     */
    private static String getLegacyOperationDisplayName(String operationDisplayName, TestDescriptor testDescriptor) {
        // if toString() is not overridden, use the display name for test operation
        if (operationDisplayName.endsWith("@" + Integer.toHexString(testDescriptor.hashCode()))) {
            return testDescriptor.getDisplayName();
        } else {
            return operationDisplayName;
        }
    }

    /**
     * can be removed once the workaround above ({@link #getLegacyOperationDisplayName(String, TestDescriptor) 1} and
     * {@link #adjustOperationDisplayNameForIntelliJ(String, AbstractTestDescriptor) 2}) are removed
     */
    private static TestDescriptor getOriginalDescriptor(TestDescriptor testDescriptor) {
        if (testDescriptor instanceof DecoratingTestDescriptor) {
            return getOriginalDescriptor(((DecoratingTestDescriptor) testDescriptor).getDescriptor());
        } else {
            return testDescriptor;
        }
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
