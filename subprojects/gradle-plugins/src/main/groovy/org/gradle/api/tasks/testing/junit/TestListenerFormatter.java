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
package org.gradle.api.tasks.testing.junit;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.TestSuite;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.remote.RemoteSender;
import org.gradle.util.shutdown.ShutdownHookActionRegister;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

public class TestListenerFormatter implements JUnitResultFormatter {
    private static final String SERVER_ADDRESS = "test.listener.server.address";
    private static TestListener defaultSender;
    private TestListener remoteSender;
    private Throwable error;

    public TestListenerFormatter() throws IOException, URISyntaxException {
        // An instance of this class is created for each test class, so use a singleton RemoteSender
        if (defaultSender == null) {
            String serverAddress = System.getProperty(SERVER_ADDRESS);
            if (serverAddress == null) {
                // This can happen when the listener is instantiated in the build process, for example, when the
                // test vm crashes
                defaultSender = new ListenerBroadcast<TestListener>(TestListener.class).getSource();
            }
            else {
                // Assume we're in the forked test process
                RemoteSender<TestListener> sender = new RemoteSender<TestListener>(TestListener.class, new URI(serverAddress));
                ShutdownHookActionRegister.closeOnExit(sender);
                defaultSender = sender.getSource();
            }
        }
        remoteSender = defaultSender;
    }

    public TestListenerFormatter(TestListener listener) throws IOException {
        // for testing
        remoteSender = listener;
    }
    
    public void startTestSuite(JUnitTest jUnitTest) throws BuildException {
        remoteSender.beforeSuite(new MySuite(jUnitTest));
    }

    public void endTestSuite(JUnitTest jUnitTest) throws BuildException {
        remoteSender.afterSuite(new MySuite(jUnitTest));
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
        remoteSender.afterTest(new MyTest(test), result);
    }

    public void startTest(Test test) {
        remoteSender.beforeTest(new MyTest(test));
    }

    private static class MySuite implements TestSuite, Serializable
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

    private static class MyTest implements org.gradle.api.tasks.testing.Test, Serializable
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

    private static class MyResult implements TestResult, Serializable
    {
        private Throwable error;

        private MyResult(Throwable error) {
            this.error = error;
        }

        public ResultType getResultType() {
            if (error != null) {
                return ResultType.FAILURE;
            }
            else {
                return ResultType.SUCCESS;
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

