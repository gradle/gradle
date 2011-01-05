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

import junit.extensions.TestSetup;
import junit.framework.*;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.util.IdGenerator;
import org.gradle.util.TimeProvider;

import java.util.IdentityHashMap;
import java.util.Map;

public class JUnitTestResultProcessorAdapter implements TestListener {
    private final TestResultProcessor resultProcessor;
    private final TimeProvider timeProvider;
    private final IdGenerator<?> idGenerator;
    private final Object lock = new Object();
    private final Map<Object, TestDescriptorInternal> executing = new IdentityHashMap<Object, TestDescriptorInternal>();

    public JUnitTestResultProcessorAdapter(TestResultProcessor resultProcessor, TimeProvider timeProvider,
                                 IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.timeProvider = timeProvider;
        this.idGenerator = idGenerator;
    }

    public void startTest(Test test) {
        TestDescriptorInternal descriptor = convert(idGenerator.generateId(), test);
        doStartTest(test, descriptor);
    }

    private void doStartTest(Test test, TestDescriptorInternal descriptor) {
        synchronized (lock) {
            TestDescriptorInternal oldTest = executing.put(test, descriptor);
            assert oldTest == null : String.format("Unexpected start event for test '%s' (class %s)", test, test.getClass());
        }
        long startTime = timeProvider.getCurrentTime();
        resultProcessor.started(descriptor, new TestStartEvent(startTime));
    }

    public void addError(Test test, Throwable throwable) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(test);
        }
        boolean needEndEvent = false;
        if (testInternal == null) {
            // this happens when @AfterClass fails, for example. Synthesise a start and end events
            testInternal = convertForError(test);
            needEndEvent = true;
            doStartTest(test, testInternal);
        }
        resultProcessor.failure(testInternal.getId(), throwable);

        if (needEndEvent) {
            endTest(test);
        }
    }

    private TestDescriptorInternal convertForError(Test test) {
        if (test instanceof TestSetup) {
            TestSetup testSetup = (TestSetup) test;
            return new DefaultTestDescriptor(idGenerator.generateId(), testSetup.getClass().getName(), "classMethod");
        }
        assert test instanceof TestSuite : String.format("Unexpected type for test '%s'. Should be TestSuite, is %s", test, test.getClass());
        TestSuite suite = (TestSuite) test;
        return new DefaultTestMethodDescriptor(idGenerator.generateId(), suite.getName(), "classMethod");
    }

    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        addError(test, assertionFailedError);
    }

    public void endTest(Test test) {
        long endTime = timeProvider.getCurrentTime();
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.remove(test);
            assert testInternal != null : String.format("Unexpected end event for test '%s' (class %s)", test, test.getClass());
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime));
    }

    protected TestDescriptorInternal convert(Object id, Test test) {
        if (test instanceof TestCase) {
            TestCase testCase = (TestCase) test;
            return new DefaultTestDescriptor(id, testCase.getClass().getName(), testCase.getName());
        }
        return new DefaultTestDescriptor(id, test.getClass().getName(), test.toString());
    }
}

