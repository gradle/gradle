/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.internal.tasks.testing.DefaultTest;
import org.gradle.api.internal.tasks.testing.DefaultTestMethod;
import org.gradle.api.internal.tasks.testing.DefaultTestResult;
import org.gradle.api.internal.tasks.testing.DefaultTestSuite;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;

public class TestNGListenerAdapter implements ITestListener {
    private TestListener remoteSender;

    public TestNGListenerAdapter(TestListener listener) throws IOException {
        remoteSender = listener;
    }

    public void onStart(ITestContext iTestContext) {
        remoteSender.beforeSuite(new DefaultTestSuite(iTestContext.getName()));
    }

    public void onFinish(ITestContext iTestContext) {
        remoteSender.afterSuite(new DefaultTestSuite(iTestContext.getName()));
    }

    public void onTestStart(ITestResult iTestResult) {
        remoteSender.beforeTest(convert(iTestResult));
    }

    public void onTestSuccess(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SUCCESS);
    }

    public void onTestFailure(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.FAILURE);
    }

    public void onTestSkipped(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SKIPPED);
    }

    public void onTestFailedButWithinSuccessPercentage(ITestResult iTestResult) {
        onTestFinished(iTestResult, TestResult.ResultType.SUCCESS);
    }

    private void onTestFinished(ITestResult iTestResult, TestResult.ResultType resultType) {
        TestResult result = new DefaultTestResult(resultType, iTestResult.getThrowable(),
                iTestResult.getStartMillis(), iTestResult.getEndMillis());
        remoteSender.afterTest(convert(iTestResult), result);
    }

    private DefaultTest convert(ITestResult iTestResult) {
        return new DefaultTestMethod(iTestResult.getTestClass().getName(), iTestResult.getName());
    }
}
