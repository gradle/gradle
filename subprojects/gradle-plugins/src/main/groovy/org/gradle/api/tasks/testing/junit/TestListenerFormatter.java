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
package org.gradle.api.tasks.testing.junit;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.apache.tools.ant.BuildException;
import org.gradle.listener.remote.RemoteSender;
import org.gradle.api.tasks.testing.TestListener;

import java.io.*;

import junit.framework.Test;
import junit.framework.AssertionFailedError;

public class TestListenerFormatter implements JUnitResultFormatter {
    private static final String PORT_VMARG = "test.listener.remote.port";
    private RemoteSender<TestListener> sender;
    private TestListener remoteSender;
    private Throwable error;

    public TestListenerFormatter() throws IOException {
        int port = Integer.parseInt(System.getProperty(PORT_VMARG, "0"));
        if (port <= 0) {
            throw new IllegalArgumentException(String.format("Invalid port '%s' specified for remote test listener.",
                    System.getProperty(PORT_VMARG)));
        }
        sender = new RemoteSender<TestListener>(TestListener.class, port);
        remoteSender = sender.getSource();
    }

    public TestListenerFormatter(TestListener listener) throws IOException {
        // for testing
        remoteSender = listener;
    }
    
    public void startTestSuite(JUnitTest jUnitTest) throws BuildException {
        remoteSender.suiteStarting(new MySuite(jUnitTest));
    }

    public void endTestSuite(JUnitTest jUnitTest) throws BuildException {
        remoteSender.suiteFinished(new MySuite(jUnitTest));
    }

    public void setOutput(OutputStream outputStream) {
    }

    public void setSystemOutput(String s) {
    }

    public void setSystemError(String s) {
    }

    public void addError(Test test, Throwable throwable) {
        error = throwable;
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        error = assertionFailedError;
    }

    public void endTest(Test test) {
        MyResult result = new MyResult(error);
        error = null;
        remoteSender.testFinished(new MyTest(test), result);
    }

    public void startTest(Test test) {
        remoteSender.testStarting(new MyTest(test));
    }

    private static class MySuite implements TestListener.Suite
    {
        private String name;

        public MySuite(JUnitTest jUnitTest) {
            name = jUnitTest.getName();
        }

        @Override
        public String toString() {
            return String.format("suite %s", name);
        }

        public String getName() {
            return name;
        }
    }

    private static class MyTest implements TestListener.Test
    {
        private String name;

        public MyTest(Test test) {
            name = test.toString();
        }

        @Override
        public String toString() {
            return String.format("test %s", name);
        }

        public String getName() {
            return name;
        }
    }

    private static class MyResult implements TestListener.Result
    {
        private Throwable error;

        private MyResult(Throwable error) {
            this.error = error;
        }

        public TestListener.ResultType getResultType() {
            if (error != null) {
                return TestListener.ResultType.FAILURE;
            }
            else {
                return TestListener.ResultType.SUCCESS;
            }
        }

        public Throwable getException() {
            if (error != null) {
                return error;
            }

            throw new IllegalStateException("No exception to return");
        }
    }
}

