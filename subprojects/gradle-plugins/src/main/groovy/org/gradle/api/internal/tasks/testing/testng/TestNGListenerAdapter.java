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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestResult;
import org.testng.*;
import org.testng.internal.IConfigurationListener;

import java.util.HashMap;
import java.util.Map;

public class TestNGListenerAdapter implements ITestListener, IConfigurationListener {
    private final TestResultProcessor resultProcessor;
    private final Object lock = new Object();
    private long nextId;
    private Map<String, TestInternal> suites = new HashMap<String, TestInternal>();
    private Map<String, TestInternal> tests = new HashMap<String, TestInternal>();

    public TestNGListenerAdapter(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    public void onStart(ITestContext iTestContext) {
        TestInternal testInternal;
        synchronized (lock) {
            Long id = nextId++;
            testInternal = new DefaultTestSuite(id, iTestContext.getName());
            suites.put(testInternal.getName(), testInternal);
        }
        resultProcessor.started(testInternal);
    }

    public void onFinish(ITestContext iTestContext) {
        TestInternal testInternal;
        synchronized (lock) {
            testInternal = suites.remove(iTestContext.getName());
        }
        resultProcessor.completed(testInternal, null);
    }

    public void onTestStart(ITestResult iTestResult) {
        TestInternal testInternal;
        synchronized (lock) {
            Long id = nextId++;
            testInternal = new DefaultTestMethod(id, iTestResult.getTestClass().getName(), iTestResult.getName());
            TestInternal oldTest = tests.put(testInternal.getName(), testInternal);
            if (oldTest != null) {
                throw new IllegalStateException(String.format(
                        "Cannot handle a test instance executing multiple times concurrently: %s", testInternal));
            }
        }
        resultProcessor.started(testInternal);
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
        TestResult result = new DefaultTestResult(resultType, iTestResult.getThrowable(), iTestResult.getStartMillis(),
                iTestResult.getEndMillis());
        TestInternal testInternal;
        synchronized (lock) {
            testInternal = tests.remove(iTestResult.getName());
        }
        resultProcessor.completed(testInternal, result);
    }

    public void onConfigurationSuccess(ITestResult testResult) {
    }

    public void onConfigurationSkip(ITestResult testResult) {
    }

    public void onConfigurationFailure(ITestResult testResult) {
        if (!testResult.isSuccess()) {
            TestInternal test = new DefaultTestMethod(0, testResult.getMethod().getTestClass().getName(),
                    testResult.getMethod().getMethodName());
            resultProcessor.completed(test, new DefaultTestResult(testResult.getThrowable(), testResult.getStartMillis(), testResult.getEndMillis()));
        }
    }
}
