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
import org.gradle.api.execution.internal.TaskOperationDescriptor;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTestResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTestFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTestFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTestSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTestStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTestSuccessResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 */
class ClientForwardingTestListener implements TestListenerInternal, InternalBuildListener {

    private final BuildEventConsumer eventConsumer;
    private final BuildClientSubscriptions clientSubscriptions;
    private Map<Object, String> runningTasks = Maps.newHashMap();

    ClientForwardingTestListener(BuildEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions) {
        this.eventConsumer = eventConsumer;
        this.clientSubscriptions = clientSubscriptions;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        eventConsumer.dispatch(new DefaultTestStartedProgressEvent(startEvent.getStartTime(), adapt(testDescriptor)));
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        eventConsumer.dispatch(new DefaultTestFinishedProgressEvent(completeEvent.getEndTime(), adapt(testDescriptor), adapt(testResult)));
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        // Don't forward
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
        if (clientSubscriptions.isSendTaskProgressEvents()) {
            return descriptor.getOwnerBuildOperationId();
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

    @Override
    public void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
        if (!(buildOperation.getOperationDescriptor() instanceof TaskOperationDescriptor)) {
            return;
        }
        TaskInternal task = ((TaskOperationDescriptor) buildOperation.getOperationDescriptor()).getTask();
        if (!(task instanceof Test)) {
            return;
        }
        runningTasks.put(buildOperation.getId(), task.getPath());
    }

    @Override
    public void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
        if (!(buildOperation.getOperationDescriptor() instanceof TaskOperationDescriptor)) {
            return;
        }
        runningTasks.remove(buildOperation.getId());
    }
}
