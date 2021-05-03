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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.build.event.types.DefaultTestDescriptor;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

class TestExecutionResultEvaluator implements BuildOperationListener {
    private static final String INDENT = "    ";

    private final BuildOperationAncestryTracker ancestryTracker;
    private final TestExecutionRequestAction internalTestExecutionRequest;

    private final AtomicLong resultCount = new AtomicLong();
    private final Map<Object, String> runningTasks = Maps.newConcurrentMap();
    private final Queue<FailedTest> failedTests = new ConcurrentLinkedQueue<FailedTest>();

    public TestExecutionResultEvaluator(
        BuildOperationAncestryTracker ancestryTracker,
        TestExecutionRequestAction internalTestExecutionRequest
    ) {
        this.ancestryTracker = ancestryTracker;
        this.internalTestExecutionRequest = internalTestExecutionRequest;
    }

    public boolean hasUnmatchedTests() {
        return resultCount.get() == 0;
    }

    public boolean hasFailedTests() {
        return !failedTests.isEmpty();
    }

    public void evaluate() {
        if (hasUnmatchedTests()) {
            String formattedTestRequest = formatInternalTestExecutionRequest();
            throw new TestExecutionException("No matching tests found in any candidate test task.\n" + formattedTestRequest);
        }
        if (hasFailedTests()) {
            StringBuilder failedTestsMessage = new StringBuilder("Test failed.\n")
                .append(INDENT).append("Failed tests:");
            for (FailedTest failedTest : failedTests) {
                failedTestsMessage.append("\n").append(Strings.repeat(INDENT, 2)).append(failedTest.getDescription());
            }
            throw new TestExecutionException(failedTestsMessage.toString());
        }
    }

    private String formatInternalTestExecutionRequest() {
        StringBuilder requestDetails = new StringBuilder(INDENT).append("Requested tests:");
        for (InternalTestDescriptor internalTestDescriptor : internalTestExecutionRequest.getTestExecutionDescriptors()) {
            requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append(internalTestDescriptor.getDisplayName());
            requestDetails.append(" (Task: '").append(((DefaultTestDescriptor) internalTestDescriptor).getTaskPath()).append("')");
        }
        final Collection<InternalJvmTestRequest> internalJvmTestRequests = internalTestExecutionRequest.getInternalJvmTestRequests();

        for (InternalJvmTestRequest internalJvmTestRequest : internalJvmTestRequests) {
            final String className = internalJvmTestRequest.getClassName();
            final String methodName = internalJvmTestRequest.getMethodName();
            if (methodName == null) {
                requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append("Test class ").append(className);
            } else {
                requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append("Test method ").append(className).append(".").append(methodName).append("()");
            }
        }
        return requestDetails.toString();
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            Task task = ((ExecuteTaskBuildOperationDetails) buildOperation.getDetails()).getTask();
            runningTasks.put(buildOperation.getId(), task.getPath());
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
            TestDescriptorInternal testDescriptor = (TestDescriptorInternal) ((ExecuteTestBuildOperationType.Details) buildOperation.getDetails()).getTestDescriptor();
            TestResult testResult = ((ExecuteTestBuildOperationType.Result) finishEvent.getResult()).getResult();
            if (testDescriptor.getParent() == null) {
                resultCount.addAndGet(testResult.getTestCount());
            }
            if (!testDescriptor.isComposite() && testResult.getFailedTestCount() != 0) {
                failedTests.add(new FailedTest(testDescriptor.getName(), testDescriptor.getClassName(), getTaskPath(buildOperation.getId(), testDescriptor)));
            }
        }
    }

    private String getTaskPath(OperationIdentifier buildOperationId, TestDescriptorInternal descriptor) {
        return ancestryTracker.findClosestExistingAncestor(buildOperationId, runningTasks::get)
            .orElseThrow(() -> new IllegalStateException("No parent task for test " + descriptor));
    }

    private static class FailedTest {
        final String name;
        final String className;
        final String taskPath;

        public FailedTest(String name, String className, String taskPath) {
            this.name = name;
            this.className = className;
            this.taskPath = taskPath;
        }

        public String getDescription() {
            StringBuilder stringBuilder = new StringBuilder("Test ")
                .append(className).append("#").append(name)
                .append(" (Task: ").append(taskPath).append(")");
            return stringBuilder.toString();

        }
    }
}
