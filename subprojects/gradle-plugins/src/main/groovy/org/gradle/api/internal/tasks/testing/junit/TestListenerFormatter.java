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

package org.gradle.api.internal.tasks.testing.junit;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.gradle.api.internal.tasks.testing.DefaultTest;
import org.gradle.api.internal.tasks.testing.DefaultTestClass;
import org.gradle.api.internal.tasks.testing.DefaultTestResult;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestSuite;
import org.gradle.util.TimeProvider;

import java.io.OutputStream;

public class TestListenerFormatter implements JUnitResultFormatter {
    private final TestListener listener;
    private final TimeProvider timeProvider;
    private Throwable error;
    private long startTime;

    public TestListenerFormatter(TestListener listener, TimeProvider timeProvider) {
        this.listener = listener;
        this.timeProvider = timeProvider;
    }

    public void startTestSuite(JUnitTest jUnitTest) throws BuildException {
        listener.beforeSuite(convert(jUnitTest));
    }

    public void endTestSuite(JUnitTest jUnitTest) throws BuildException {
        listener.afterSuite(convert(jUnitTest));
    }

    public void setOutput(OutputStream outputStream) {
    }

    public void setSystemOutput(String s) {
    }

    public void setSystemError(String s) {
    }

    public void startTest(Test test) {
        startTime = timeProvider.getCurrentTime();
        listener.beforeTest(convert(test));
    }

    public void addError(Test test, Throwable throwable) {
        error = throwable;
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        error = assertionFailedError;
    }

    public void endTest(Test test) {
        long endTime = timeProvider.getCurrentTime();
        DefaultTestResult result = new DefaultTestResult(error, startTime, endTime);
        error = null;
        listener.afterTest(convert(test), result);
    }

    private TestSuite convert(JUnitTest jUnitTest) {
        String className = jUnitTest.getName();
        return new DefaultTestClass(className);
    }

    protected DefaultTest convert(Test test) {
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase) test;
            return new DefaultTest(testCase.getClass().getName(), testCase.getName());
        }
        return new DefaultTest(test.getClass().getName(), test.toString());
    }
}

