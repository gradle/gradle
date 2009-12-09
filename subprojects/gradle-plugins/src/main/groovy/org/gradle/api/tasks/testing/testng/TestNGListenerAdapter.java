/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.testing.testng;

/**
 * This is a shell to show what I wanted the TestNG listener to look like.  Right now, Gradle cannot depend on TestNG
 * or we will lock down support to that given version.  This was deemed bad, so I'm not using this code right now.
 * At some point in the future, Gradle will need to be able to compile against one version of a tool (like TestNG or
 * JUnit) and run against another version.  Right now, this apparently does not work (based on developer mailing list
 * discusions).
 */
public class TestNGListenerAdapter {
}

/*
import org.testng.ITestListener;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.gradle.listener.remote.RemoteSender;
import org.gradle.api.tasks.testing.TestListener;
import java.io.*;

public class TestNGListenerAdapter implements ITestListener {
    private static final String PORT_VMARG = "testng.listener.remote.port";
    private TestListener remoteSender;

    public TestNGListenerAdapter() throws IOException {
        int port = Integer.parseInt(System.getProperty(PORT_VMARG, "0"));
        if (port != 0) {
            remoteSender = new RemoteSender<TestListener>(TestListener.class, port).getSource();
        }
    }

    public TestNGListenerAdapter(TestListener listener) throws IOException {
        // for testing
        remoteSender = listener;
    }

    public void onStart(ITestContext iTestContext) {
        remoteSender.suiteStarting(new MySuite(iTestContext));
    }

    public void onFinish(ITestContext iTestContext) {
        remoteSender.suiteFinished(new MySuite(iTestContext));
    }

    public void onTestStart(ITestResult iTestResult) {
        remoteSender.testStarting(new MyTest(iTestResult));
    }

    private void onTestFinished(ITestResult iTestResult) {
        remoteSender.testFinished(new MyTest(iTestResult), new MyResult(iTestResult));
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

    private static class MySuite implements TestListener.Suite {
        private String name;

        public MySuite(ITestContext iTestContext) {
            name = iTestContext.getName();
        }

        public String getName() {
            return name;
        }
    }

    private static class MyTest implements TestListener.Test {
        private String name;

        public MyTest(ITestResult iTestResult) {
            name = iTestResult.getName();
        }

        public String getName() {
            return name;
        }
    }

    private static class MyResult implements TestListener.Result {
        private TestListener.ResultType result;
        private Throwable error;

        private MyResult(ITestResult iTestResult) {
            switch(iTestResult.getStatus()) {
                case ITestResult.SUCCESS:
                case ITestResult.SUCCESS_PERCENTAGE_FAILURE:
                    result = TestListener.ResultType.SUCCESS;
                    break;
                case ITestResult.FAILURE:
                    result = TestListener.ResultType.FAILURE;
                    error = iTestResult.getThrowable();
                    break;
                case ITestResult.SKIP:
                    result = TestListener.ResultType.SKIPPED;
                    break;
                default:
                    result = TestListener.ResultType.FAILURE;
                    error = new Error("Unexpected result status \'" + iTestResult.getStatus() + "\' returned from TestNG.");
            }
        }

        public TestListener.ResultType getResultType() {
            return result;
        }

        public Throwable getException() {
            if (error != null) {
                return error;
            }

            throw new IllegalStateException("No exception to return");
        }
    }
}*/
