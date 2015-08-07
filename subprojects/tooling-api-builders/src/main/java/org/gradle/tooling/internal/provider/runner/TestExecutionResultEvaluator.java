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

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequestVersion2;
import org.gradle.tooling.internal.protocol.test.InternalTestMethod;
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor;

class TestExecutionResultEvaluator implements TestListener {
    private static final String INDENT = "    ";

    private long resultCount;
    private boolean didJob;
    private long failedTestCount;
    private InternalTestExecutionRequestVersion2 internalTestExecutionRequest;

    public TestExecutionResultEvaluator(InternalTestExecutionRequestVersion2 internalTestExecutionRequest) {
        this.internalTestExecutionRequest = internalTestExecutionRequest;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        didJob = true;
        if (suite.getParent() == null) {
            resultCount = resultCount + result.getTestCount();
            failedTestCount = failedTestCount + result.getFailedTestCount();
        }
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
    }

    @Override
    public void afterTest(TestDescriptor test, TestResult result) {
    }

    public boolean hasUnmatchedTests() {
        return resultCount == 0 && didJob; // up-to-date tests tasks are ignored
    }

    public boolean hasFailedTests() {
        return failedTestCount != 0 && didJob; // up-to-date tests tasks are ignored
    }

    public void evaluate() {
        if (hasUnmatchedTests()) {
            String formattedTestRequest = formatInternalTestExecutionRequest();
            throw new TestExecutionException("No matching tests found in any candidate test task.\n" + formattedTestRequest);
        }
        if (hasFailedTests()) {
            throw new TestExecutionException("Test(s) failed!");
        }
    }

    private String formatInternalTestExecutionRequest() {
        StringBuffer requestDetails = new StringBuffer(INDENT).append("Requested Tests:");
        for (InternalTestDescriptor internalTestDescriptor : internalTestExecutionRequest.getTestExecutionDescriptors()) {
            requestDetails.append("\n").append(INDENT).append(INDENT).append(internalTestDescriptor.getDisplayName());
            requestDetails.append(" (Task: '").append(((DefaultTestDescriptor) internalTestDescriptor).getTaskPath()).append("')");
        }
        for (String testClass : internalTestExecutionRequest.getTestClassNames()) {
            requestDetails.append("\n").append(INDENT).append(INDENT).append("Test class ").append(testClass);
        }
        for (InternalTestMethod testMethod : internalTestExecutionRequest.getTestMethods()) {
            requestDetails.append("\n").append(INDENT).append(INDENT).append("Test method ").append(testMethod.getDescription());
        }
        return requestDetails.toString();
    }
}
