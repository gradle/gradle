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

import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.TestSuite;
import org.testng.ITestListener;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.gradle.api.tasks.testing.TestListener;

import java.io.*;

public class TestNGListenerAdapter implements ITestListener {
    private TestListener remoteSender;

    public TestNGListenerAdapter(TestListener listener) throws IOException {
        remoteSender = listener;
    }

    public void onStart(ITestContext iTestContext) {
        remoteSender.beforeSuite(new MySuite(iTestContext));
    }

    public void onFinish(ITestContext iTestContext) {
        remoteSender.afterSuite(new MySuite(iTestContext));
    }

    public void onTestStart(ITestResult iTestResult) {
        remoteSender.beforeTest(new MyTest(iTestResult));
    }

    private void onTestFinished(ITestResult iTestResult) {
        remoteSender.afterTest(new MyTest(iTestResult), new MyResult(iTestResult));
    }

    public void onTestSuccess(ITestResult iTestResult) {
        onTestFinished(iTestResult);
    }

    public void onTestFailure(ITestResult iTestResult) {
        onTestFinished(iTestResult);
    }

    public void onTestSkipped(ITestResult iTestResult) {
        onTestFinished(iTestResult);
    }

    public void onTestFailedButWithinSuccessPercentage(ITestResult iTestResult) {
        onTestFinished(iTestResult);
    }

    private static class MySuite implements TestSuite, Serializable {
        private String name;

        public MySuite(ITestContext iTestContext) {
            name = iTestContext.getName();
        }

        public String getName() {
            return name;
        }
    }

    private static class MyTest implements Test, Serializable {
        private String name;

        public MyTest(ITestResult iTestResult) {
            name = iTestResult.getName();
        }

        public String getName() {
            return name;
        }
    }

    private static class MyResult implements TestResult, Serializable {
        private ResultType result;
        private Throwable error;

        private MyResult(ITestResult iTestResult) {
            switch (iTestResult.getStatus()) {
                case ITestResult.SUCCESS:
                case ITestResult.SUCCESS_PERCENTAGE_FAILURE:
                    result = ResultType.SUCCESS;
                    break;
                case ITestResult.FAILURE:
                    result = ResultType.FAILURE;
                    error = iTestResult.getThrowable();
                    break;
                case ITestResult.SKIP:
                    result = ResultType.SKIPPED;
                    break;
                default:
                    result = ResultType.FAILURE;
                    error = new Error(
                            "Unexpected result status \'" + iTestResult.getStatus() + "\' returned from TestNG.");
            }
        }

        public ResultType getResultType() {
            return result;
        }

        public Throwable getException() {
            if (error != null) {
                return error;
            }

            throw new IllegalStateException("No exception to return");
        }
    }
}
