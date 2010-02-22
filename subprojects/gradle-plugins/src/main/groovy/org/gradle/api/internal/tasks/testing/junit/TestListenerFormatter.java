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
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;

import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

public class TestListenerFormatter implements JUnitResultFormatter {
    private final TestResultProcessor resultProcessor;
    private final TimeProvider timeProvider;
    private final IdGenerator<?> idGenerator;
    private final Object lock = new Object();
    private final Map<Object, TestInternalDescriptor> executing = new IdentityHashMap<Object, TestInternalDescriptor>();

    public TestListenerFormatter(TestResultProcessor resultProcessor, TimeProvider timeProvider, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    public void startTestSuite(JUnitTest jUnitTest) throws BuildException {
        TestInternalDescriptor testInternal;
        synchronized (lock) {
            testInternal = convert(idGenerator.generateId(), jUnitTest);
            executing.put(jUnitTest, testInternal);
        }
        long startTime = timeProvider.getCurrentTime();
        resultProcessor.started(testInternal, new TestStartEvent(startTime));
    }

    public void endTestSuite(JUnitTest jUnitTest) throws BuildException {
        long endTime = timeProvider.getCurrentTime();
        TestInternalDescriptor testInternal;
        synchronized (lock) {
            testInternal = executing.remove(jUnitTest);
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime));
    }

    public void setOutput(OutputStream outputStream) {
    }

    public void setSystemOutput(String s) {
    }

    public void setSystemError(String s) {
    }

    public void startTest(Test test) {
        TestInternalDescriptor testInternal;
        synchronized (lock) {
            testInternal = convert(idGenerator.generateId(), test);
            TestInternalDescriptor oldTest = executing.put(test, testInternal);
            if (oldTest != null) {
                throw new IllegalStateException(String.format(
                        "Cannot handle a test instance executing multiple times concurrently: %s", testInternal));
            }
        }
        long startTime = timeProvider.getCurrentTime();
        resultProcessor.started(testInternal, new TestStartEvent(startTime));
    }

    public void addError(Test test, Throwable throwable) {
        TestInternalDescriptor testInternal;
        synchronized (lock) {
            testInternal = executing.get(test);
        }
        resultProcessor.addFailure(testInternal.getId(), throwable);
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        addError(test, assertionFailedError);
    }

    public void endTest(Test test) {
        long endTime = timeProvider.getCurrentTime();
        TestInternalDescriptor testInternal;
        synchronized (lock) {
            testInternal = executing.remove(test);
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime));
    }

    private TestInternalDescriptor convert(Object id, JUnitTest jUnitTest) {
        String className = jUnitTest.getName();
        return new DefaultTestClassDescriptor(id, className);
    }

    protected TestInternalDescriptor convert(Object id, Test test) {
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase) test;
            return new DefaultTestDescriptor(id, testCase.getClass().getName(), testCase.getName());
        }
        return new DefaultTestDescriptor(id, test.getClass().getName(), test.toString());
    }
}

