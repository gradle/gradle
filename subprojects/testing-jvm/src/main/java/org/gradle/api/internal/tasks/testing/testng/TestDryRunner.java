/*
 * Copyright 2022 the original author or authors.
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

import org.testng.IInvokedMethodListener;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.IResultMap;
import org.testng.ISuite;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.TestRunner;
import org.testng.collections.Lists;
import org.testng.internal.IConfiguration;
import org.testng.internal.MethodInstance;
import org.testng.internal.ResultMap;
import org.testng.internal.TestResult;
import org.testng.xml.XmlTest;

import java.util.Date;
import java.util.List;

class TestDryRunner extends TestRunner {
    private Date startDate = null;
    private Date endDate = null;
    private final IMethodInterceptor methodInterceptor;

    public TestDryRunner(
        IConfiguration configuration,
        ISuite suite,
        XmlTest test,
        boolean skipFailedInvocationCounts,
        List<IInvokedMethodListener> listeners,
        IMethodInterceptor methodInterceptor
    ) {
        super(configuration, suite, test, skipFailedInvocationCounts, listeners);
        this.methodInterceptor = methodInterceptor;
    }

    @Override
    public void run() {
        startDate = new Date(System.currentTimeMillis());
        fireEvent(true);

        for (ITestResult testResult : getPassedTests().getAllResults()) {
            for (ITestListener testListener : getTestListeners()) {
                testListener.onTestSuccess(testResult);
            }
        }

        endDate = new Date(System.currentTimeMillis());
        fireEvent(false);
    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public Date getEndDate() {
        return endDate;
    }

    @Override
    public IResultMap getPassedTests() {
        IResultMap resultMap = new ResultMap();

        List<IMethodInstance> allTestMethods = Lists.newArrayList();

        for (ITestNGMethod testNGMethod : getAllTestMethods()) {
            allTestMethods.add(new MethodInstance(testNGMethod));
        }

        List<IMethodInstance> filteredTestMethods = methodInterceptor.intercept(allTestMethods, this);

        for (IMethodInstance method : filteredTestMethods) {
            TestResult result = new TestResult(method.getMethod().getTestClass(), method.getInstance(), method.getMethod(), null, 0, 0);
            result.setStatus(ITestResult.SUCCESS);
            resultMap.addResult(result, method.getMethod());
        }

        return resultMap;
    }

    private void fireEvent(boolean isStart) {
        for (ITestListener itl : getTestListeners()) {
            if (isStart) {
                itl.onStart(this);
            } else {
                itl.onFinish(this);
            }
        }
    }
}
