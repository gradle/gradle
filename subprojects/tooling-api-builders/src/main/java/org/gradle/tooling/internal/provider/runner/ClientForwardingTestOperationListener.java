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
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTestResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTestFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTestFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTestSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTestStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTestSuccessResult2; // TODO delete old DefaultTestSuccessResult?

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 */
class ClientForwardingTestOperationListener implements BuildOperationListener {

    private final ProgressEventConsumer eventConsumer;
    private final BuildClientSubscriptions clientSubscriptions;
    private final Map<Object, String> runningTasks = Maps.newConcurrentMap();

    ClientForwardingTestOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions) {
        this.eventConsumer = eventConsumer;
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
            runningTasks.put(buildOperation.getId(), task.getPath());
        } else if (details instanceof ExecuteTestBuildOperationType.Details) {
            ExecuteTestBuildOperationType.Details testOperationDetails = (ExecuteTestBuildOperationType.Details) details;
            TestDescriptorInternal testDescriptor = (TestDescriptorInternal) testOperationDetails.getTestDescriptor();
            eventConsumer.started(new DefaultTestStartedProgressEvent(testOperationDetails.getStartTime(), adapt(testDescriptor)));
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            runningTasks.remove(buildOperation.getId());
        } else if (finishEvent.getResult() instanceof ExecuteTestBuildOperationType.Result) {
            TestResult testResult = ((ExecuteTestBuildOperationType.Result) finishEvent.getResult()).getResult();
            TestDescriptorInternal testDescriptor = (TestDescriptorInternal) ((ExecuteTestBuildOperationType.Details) buildOperation.getDetails()).getTestDescriptor();
            eventConsumer.finished(new DefaultTestFinishedProgressEvent(testResult.getEndTime(), adapt(testDescriptor), adapt(testResult)));
        }
    }

    private DefaultTestDescriptor adapt(TestDescriptorInternal testDescriptor) {
        return testDescriptor.isComposite() ? toTestDescriptorForSuite(testDescriptor) : toTestDescriptorForTest(testDescriptor);
    }

    private DefaultTestDescriptor toTestDescriptorForSuite(TestDescriptorInternal suite) {
        Object id = suite.getId();
        String name = suite.getName();
        String displayName = suite.toString();
        String testKind = InternalJvmTestDescriptor.KIND_SUITE;
        String suiteName = suite.getName();
        String className = suite.getClassName();
        String methodName = null;
        Object parentId = getParentId(suite);
        final String testTaskPath = getTaskPath(suite);
        return new DefaultTestDescriptor(id, name, displayName, testKind, suiteName, className, methodName, parentId, testTaskPath);
    }

    private DefaultTestDescriptor toTestDescriptorForTest(TestDescriptorInternal test) {
        Object id = test.getId();
        String name = test.getName();
        String displayName = test.toString();
        String testKind = InternalJvmTestDescriptor.KIND_ATOMIC;
        String suiteName = null;
        String className = test.getClassName();
        String methodName = test.getName();
        Object parentId = getParentId(test);
        final String taskPath = getTaskPath(test);
        return new DefaultTestDescriptor(id, name, displayName, testKind, suiteName, className, methodName, parentId, taskPath);
    }

    private String getTaskPath(TestDescriptorInternal givenDescriptor) {
        TestDescriptorInternal descriptor = givenDescriptor;
        while (descriptor.getOwnerBuildOperationId() == null && descriptor.getParent() != null) {
            descriptor = descriptor.getParent();
        }
        return runningTasks.get(descriptor.getOwnerBuildOperationId());
    }

    private Object getParentId(TestDescriptorInternal descriptor) {
        TestDescriptorInternal parent = descriptor.getParent();
        if (parent != null) {
            return parent.getId();
        }
        // only set the TaskOperation as the parent if the Tooling API Consumer is listening to task progress events
        if (clientSubscriptions.isRequested(OperationType.TASK)) {
            return descriptor.getOwnerBuildOperationId();
        }
        return null;
    }

    private static AbstractTestResult adapt(TestResult result) {
        TestResult.ResultType resultType = result.getResultType();
        switch (resultType) {
            case SUCCESS:
                return new DefaultTestSuccessResult2(result.getStartTime(), result.getEndTime(), result.getOutput());
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
