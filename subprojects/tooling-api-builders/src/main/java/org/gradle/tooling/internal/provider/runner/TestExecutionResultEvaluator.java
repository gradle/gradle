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

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.gradle.api.execution.internal.InternalTaskExecutionListener;
import org.gradle.api.execution.internal.TaskOperationInternal;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequestVersion2;
import org.gradle.tooling.internal.protocol.test.InternalTestMethod;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;

import java.util.List;
import java.util.Map;

class TestExecutionResultEvaluator implements TestListenerInternal, InternalTaskExecutionListener {
    private static final String INDENT = "    ";

    private long resultCount;
    private Map<Object, String> runningTasks = Maps.newHashMap();

    private InternalTestExecutionRequestVersion2 internalTestExecutionRequest;
    private List<FailedTest> failedTests = Lists.newArrayList();

    public TestExecutionResultEvaluator(InternalTestExecutionRequestVersion2 internalTestExecutionRequest) {
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
            StringBuffer failedTestsMessage = new StringBuffer("Test failed.\n")
                .append(INDENT).append("Failed tests:");
            List<Throwable> causes = Lists.newArrayList();
            for (FailedTest failedTest : failedTests) {
                failedTestsMessage.append("\n").append(Strings.repeat(INDENT, 2)).append(failedTest.getDescription());
                causes.addAll(failedTest.testResult.getExceptions());
            }
            throw new TestExecutionException(failedTestsMessage.toString(), causes);
        }
    }

    private String formatInternalTestExecutionRequest() {
        StringBuffer requestDetails = new StringBuffer(INDENT).append("Requested tests:");
        for (InternalTestDescriptor internalTestDescriptor : internalTestExecutionRequest.getTestExecutionDescriptors()) {
            requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append(internalTestDescriptor.getDisplayName());
            requestDetails.append(" (Task: '").append(((DefaultTestDescriptor) internalTestDescriptor).getTaskPath()).append("')");
        }
        for (String testClass : internalTestExecutionRequest.getTestClassNames()) {
            requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append("Test class ").append(testClass);
        }
        for (InternalTestMethod testMethod : internalTestExecutionRequest.getTestMethods()) {
            requestDetails.append("\n").append(Strings.repeat(INDENT, 2)).append("Test method ").append(testMethod.getDescription());
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
            failedTests.add(new FailedTest(testDescriptor.getName(), testDescriptor.getClassName(), getTaskPath(testDescriptor), testResult));
        }
    }

    private String getTaskPath(TestDescriptorInternal givenDescriptor) {
        TestDescriptorInternal descriptor = givenDescriptor;
        while (descriptor.getOwnerBuildOperationId() == null && descriptor.getParent() != null) {
            descriptor = descriptor.getParent();
        }
        return runningTasks.get(descriptor.getOwnerBuildOperationId());
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {

    }

    @Override
    public void beforeExecute(TaskOperationInternal taskOperation, OperationStartEvent startEvent) {
        runningTasks.put(taskOperation.getId(), taskOperation.getTask().getPath());
    }

    @Override
    public void afterExecute(TaskOperationInternal taskOperation, OperationResult result) {
        runningTasks.remove(taskOperation.getId());
    }

    private class FailedTest {
        final String name;
        final String className;
        final String taskPath;
        final TestResult testResult;

        public FailedTest(String name, String className, String taskPath, TestResult testResult) {
            this.name = name;
            this.className = className;
            this.taskPath = taskPath;
            this.testResult = testResult;
        }

        public String getDescription() {
            StringBuilder stringBuilder = new StringBuilder("Test ")
                .append(className).append("#").append(name)
                .append(" (Task: ").append(taskPath).append(")");
            return stringBuilder.toString();

        }
    }
}
