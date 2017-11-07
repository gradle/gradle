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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Task;
import org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationProgressEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.gradle.tooling.internal.provider.TestExecutionRequestAction;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class TestExecutionResultEvaluator implements TestListenerInternal, BuildOperationListener {
    private static final String INDENT = "    ";

    private long resultCount;
    private Map<Object, String> runningTasks = Maps.newHashMap();

    private TestExecutionRequestAction internalTestExecutionRequest;
    private List<FailedTest> failedTests = Lists.newArrayList();

    public TestExecutionResultEvaluator(TestExecutionRequestAction internalTestExecutionRequest) {
        this.internalTestExecutionRequest = internalTestExecutionRequest;
    }

    public boolean hasUnmatchedTests() {
        return resultCount == 0;
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
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {

    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        if (testDescriptor.getParent() == null) {
            resultCount = resultCount + testResult.getTestCount();
        }
        if (!testDescriptor.isComposite() && testResult.getFailedTestCount() != 0) {
            failedTests.add(new FailedTest(testDescriptor.getName(), testDescriptor.getClassName(), getTaskPath(testDescriptor)));
        }
    }

    private String getTaskPath(TestDescriptorInternal givenDescriptor) {
        TestDescriptorInternal descriptor = givenDescriptor;
        while (descriptor.getOwnerBuildOperationId() == null && descriptor.getParent() != null) {
            descriptor = descriptor.getParent();
        }
        String taskPath = runningTasks.get(descriptor.getOwnerBuildOperationId());
        if (taskPath == null) {
            throw new IllegalStateException("No parent task for test " + givenDescriptor);
        }
        return taskPath;
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (!(buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails)) {
            return;
        }
        Task task = ((ExecuteTaskBuildOperationDetails) buildOperation.getDetails()).getTask();
        runningTasks.put(buildOperation.getId(), task.getPath());
    }

    @Override
    public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (!(buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails)) {
            return;
        }
        runningTasks.remove(buildOperation.getId());
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
