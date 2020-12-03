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

import com.google.common.collect.Maps;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractTestResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultTestDescriptor;
import org.gradle.internal.build.event.types.DefaultTestFailureResult;
import org.gradle.internal.build.event.types.DefaultTestFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTestSkippedResult;
import org.gradle.internal.build.event.types.DefaultTestStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTestSuccessResult;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 */
class ClientForwardingTestOperationListener implements BuildOperationListener {

    private final ProgressEventConsumer eventConsumer;
    private final BuildOperationAncestryTracker ancestryTracker;
    private final BuildEventSubscriptions clientSubscriptions;
    private final Map<Object, String> runningTasks = Maps.newConcurrentMap();

    ClientForwardingTestOperationListener(ProgressEventConsumer eventConsumer, BuildOperationAncestryTracker ancestryTracker, BuildEventSubscriptions clientSubscriptions) {
        this.eventConsumer = eventConsumer;
        this.ancestryTracker = ancestryTracker;
        this.clientSubscriptions = clientSubscriptions;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        Object details = buildOperation.getDetails();
        if (details instanceof ExecuteTaskBuildOperationDetails) {
            Task task = ((ExecuteTaskBuildOperationDetails) details).getTask();
            if (!(task instanceof Test)) {
                return;
            }
            runningTasks.put(buildOperation.getId(), ((Test) task).getIdentityPath().getPath());
        } else if (details instanceof ExecuteTestBuildOperationType.Details) {
            ExecuteTestBuildOperationType.Details testOperationDetails = (ExecuteTestBuildOperationType.Details) details;
            TestDescriptorInternal testDescriptor = (TestDescriptorInternal) testOperationDetails.getTestDescriptor();
            eventConsumer.started(new DefaultTestStartedProgressEvent(testOperationDetails.getStartTime(), adapt(buildOperation.getId(), testDescriptor)));
        }
    }

    @Override
    public void progress(@Nullable OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            runningTasks.remove(buildOperation.getId());
        } else if (finishEvent.getResult() instanceof ExecuteTestBuildOperationType.Result) {
            TestResult testResult = ((ExecuteTestBuildOperationType.Result) finishEvent.getResult()).getResult();
            TestDescriptorInternal testDescriptor = (TestDescriptorInternal) ((ExecuteTestBuildOperationType.Details) buildOperation.getDetails()).getTestDescriptor();
            eventConsumer.finished(new DefaultTestFinishedProgressEvent(testResult.getEndTime(), adapt(buildOperation.getId(), testDescriptor), adapt(testResult)));
        }
    }

    private DefaultTestDescriptor adapt(OperationIdentifier buildOperationId, TestDescriptorInternal testDescriptor) {
        return testDescriptor.isComposite() ? toTestDescriptorForSuite(buildOperationId, testDescriptor) : toTestDescriptorForTest(buildOperationId, testDescriptor);
    }

    private DefaultTestDescriptor toTestDescriptorForSuite(OperationIdentifier buildOperationId, TestDescriptorInternal suite) {
        Object id = suite.getId();
        String name = suite.getName();
        String displayName = backwardsCompatibleDisplayNameOf(suite);
        String testKind = InternalJvmTestDescriptor.KIND_SUITE;
        String className = suite.getClassName();
        String methodName = null;
        Object parentId = getParentId(buildOperationId, suite);
        String testTaskPath = getTaskPath(buildOperationId);
        return new DefaultTestDescriptor(id, name, displayName, testKind, suite.getName(), className, methodName, parentId, testTaskPath);
    }

    private DefaultTestDescriptor toTestDescriptorForTest(OperationIdentifier buildOperationId, TestDescriptorInternal test) {
        Object id = test.getId();
        String name = test.getName();
        String displayName = backwardsCompatibleDisplayNameOf(test);
        String testKind = InternalJvmTestDescriptor.KIND_ATOMIC;
        String className = test.getClassName();
        String methodName = test.getName();
        Object parentId = getParentId(buildOperationId, test);
        String taskPath = getTaskPath(buildOperationId);
        return new DefaultTestDescriptor(id, name, displayName, testKind, null, className, methodName, parentId, taskPath);
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

    @Nullable
    private String getTaskPath(OperationIdentifier buildOperationId) {
        return ancestryTracker.findClosestExistingAncestor(buildOperationId, runningTasks::get)
            .orElse(null);
    }

    private Object getParentId(OperationIdentifier buildOperationId, TestDescriptorInternal descriptor) {
        TestDescriptorInternal parent = descriptor.getParent();
        if (parent != null) {
            return parent.getId();
        }
        // only set the TaskOperation as the parent if the Tooling API Consumer is listening to task progress events
        if (clientSubscriptions.isRequested(OperationType.TASK)) {
            return ancestryTracker.findClosestMatchingAncestor(buildOperationId, runningTasks::containsKey)
                .orElse(null);
        }
        return null;
    }

    private static AbstractTestResult adapt(TestResult result) {
        TestResult.ResultType resultType = result.getResultType();
        switch (resultType) {
            case SUCCESS:
                return new DefaultTestSuccessResult(result.getStartTime(), result.getEndTime());
            case SKIPPED:
                return new DefaultTestSkippedResult(result.getStartTime(), result.getEndTime());
            case FAILURE:
                return new DefaultTestFailureResult(result.getStartTime(), result.getEndTime(), convertExceptions(result.getExceptions()));
            default:
                throw new IllegalStateException("Unknown test result type: " + resultType);
        }
    }

    private static List<DefaultFailure> convertExceptions(List<Throwable> exceptions) {
        List<DefaultFailure> failures = new ArrayList<DefaultFailure>(exceptions.size());
        for (Throwable exception : exceptions) {
            failures.add(DefaultFailure.fromThrowable(exception));
        }
        return failures;
    }

}
