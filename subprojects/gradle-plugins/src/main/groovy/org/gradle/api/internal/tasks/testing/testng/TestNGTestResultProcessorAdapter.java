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
import org.gradle.util.IdGenerator;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.IConfigurationListener;

import java.util.HashMap;
import java.util.Map;

public class TestNGTestResultProcessorAdapter implements ITestListener, IConfigurationListener {
    private final TestResultProcessor resultProcessor;
    private final IdGenerator<?> idGenerator;
    private final Object lock = new Object();
    private Map<String, Object> suites = new HashMap<String, Object>();
    private Map<String, Object> tests = new HashMap<String, Object>();
    private Map<ITestNGMethod, Object> testMethodToSuiteMapping = new HashMap<ITestNGMethod, Object>();

    public TestNGTestResultProcessorAdapter(TestResultProcessor resultProcessor, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.idGenerator = idGenerator;
    }

    public void onStart(ITestContext iTestContext) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = new DefaultTestSuiteDescriptor(idGenerator.generateId(), iTestContext.getName());
            suites.put(testInternal.getName(), testInternal.getId());
            for (ITestNGMethod method : iTestContext.getAllTestMethods()) {
                testMethodToSuiteMapping.put(method, testInternal.getId());
            }
        }
        resultProcessor.started(testInternal, new TestStartEvent(iTestContext.getStartDate().getTime()));
    }

    public void onFinish(ITestContext iTestContext) {
        Object id;
        synchronized (lock) {
            id = suites.remove(iTestContext.getName());
            for (ITestNGMethod method : iTestContext.getAllTestMethods()) {
                testMethodToSuiteMapping.remove(method);
            }
        }
        resultProcessor.completed(id, new TestCompleteEvent(iTestContext.getEndDate().getTime()));
    }

    public void onTestStart(ITestResult iTestResult) {
        TestDescriptorInternal testInternal;
        Object parentId;
        synchronized (lock) {
            testInternal = new DefaultTestMethodDescriptor(idGenerator.generateId(), iTestResult.getTestClass().getName(), iTestResult.getName());
            Object oldTestId = tests.put(testInternal.getName(), testInternal.getId());
            assert oldTestId == null;
            parentId = testMethodToSuiteMapping.get(iTestResult.getMethod());
            assert parentId != null;
        }
        resultProcessor.started(testInternal, new TestStartEvent(iTestResult.getStartMillis(), parentId));
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
        Object testId;
        TestStartEvent startEvent = null;
        synchronized (lock) {
            testId = tests.remove(iTestResult.getName());
            if (testId == null) {
                // This can happen when a method fails which this method depends on 
                testId = idGenerator.generateId();
                Object parentId = testMethodToSuiteMapping.get(iTestResult.getMethod());
                startEvent = new TestStartEvent(iTestResult.getStartMillis(), parentId);
            }
        }
        if (startEvent != null) {
            // Synthesize a start event
            resultProcessor.started(new DefaultTestMethodDescriptor(testId, iTestResult.getTestClass().getName(), iTestResult.getName()), startEvent);
        }
        if (resultType == TestResult.ResultType.FAILURE) {
            resultProcessor.failure(testId, iTestResult.getThrowable());
        }
        resultProcessor.completed(testId, new TestCompleteEvent(iTestResult.getEndMillis(), resultType));
    }

    public void onConfigurationSuccess(ITestResult testResult) {
    }

    public void onConfigurationSkip(ITestResult testResult) {
    }

    public void onConfigurationFailure(ITestResult testResult) {
        // Synthesise a test for the broken configuration method
        TestDescriptorInternal test = new DefaultTestMethodDescriptor(idGenerator.generateId(),
                testResult.getMethod().getTestClass().getName(), testResult.getMethod().getMethodName());
        resultProcessor.started(test, new TestStartEvent(testResult.getStartMillis()));
        resultProcessor.failure(test.getId(), testResult.getThrowable());
        resultProcessor.completed(test.getId(), new TestCompleteEvent(testResult.getEndMillis(), TestResult.ResultType.FAILURE));
    }
}
