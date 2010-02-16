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
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.util.TimeProvider;

import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

public class TestListenerFormatter implements JUnitResultFormatter {
    private final TestResultProcessor resultProcessor;
    private final TimeProvider timeProvider;
    private final Object lock = new Object();
    private long nextId;
    private final Map<Object, TestState> executing = new IdentityHashMap<Object, TestState>();

    public TestListenerFormatter(TestResultProcessor resultProcessor, TimeProvider timeProvider) {
        this.resultProcessor = resultProcessor;
        this.timeProvider = timeProvider;
    }

    public void startTestSuite(JUnitTest jUnitTest) throws BuildException {
        TestInternal testInternal;
        synchronized (lock) {
            Long id = nextId++;
            testInternal = convert(id, jUnitTest);
            executing.put(jUnitTest, new TestState(testInternal, 0));
        }
        resultProcessor.started(testInternal);
    }

    public void endTestSuite(JUnitTest jUnitTest) throws BuildException {
        TestInternal testInternal;
        synchronized (lock) {
            testInternal = executing.remove(jUnitTest).test;
        }
        resultProcessor.completed(testInternal, null);
    }

    public void setOutput(OutputStream outputStream) {
    }

    public void setSystemOutput(String s) {
    }

    public void setSystemError(String s) {
    }

    public void startTest(Test test) {
        long startTime = timeProvider.getCurrentTime();
        TestInternal testInternal;
        synchronized (lock) {
            Long id = nextId++;
            testInternal = convert(id, test);
            TestState oldState = executing.put(test, new TestState(testInternal, startTime));
            if (oldState != null) {
                throw new IllegalStateException(String.format(
                        "Cannot handle a test instance executing multiple times concurrently: %s", testInternal));
            }
        }
        resultProcessor.started(testInternal);
    }

    public void addError(Test test, Throwable throwable) {
        synchronized (lock) {
            executing.get(test).failure = throwable;
        }
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        addError(test, assertionFailedError);
    }

    public void endTest(Test test) {
        long endTime = timeProvider.getCurrentTime();
        TestInternal testInternal;
        DefaultTestResult result;
        synchronized (lock) {
            TestState state = executing.remove(test);
            testInternal = state.test;
            result = new DefaultTestResult(state.failure, state.startTime, endTime);
        }
        resultProcessor.completed(testInternal, result);
    }

    private TestInternal convert(Long id, JUnitTest jUnitTest) {
        String className = jUnitTest.getName();
        return new DefaultTestClass(id, className);
    }

    protected TestInternal convert(Long id, Test test) {
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase) test;
            return new DefaultTest(id, testCase.getClass().getName(), testCase.getName());
        }
        return new DefaultTest(id, test.getClass().getName(), test.toString());
    }

    private static class TestState {
        private final TestInternal test;
        private final long startTime;
        private Throwable failure;

        private TestState(TestInternal test, long startTime) {
            this.test = test;
            this.startTime = startTime;
        }
    }
}

