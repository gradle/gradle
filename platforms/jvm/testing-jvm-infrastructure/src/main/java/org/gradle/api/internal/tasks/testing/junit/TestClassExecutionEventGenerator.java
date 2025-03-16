/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;

import java.util.LinkedHashSet;
import java.util.Set;

public class TestClassExecutionEventGenerator implements TestResultProcessor, TestClassExecutionListener {
    private final TestResultProcessor resultProcessor;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final Set<Object> currentTests = new LinkedHashSet<Object>();
    private boolean testsStarted;
    private TestDescriptorInternal currentTestClass;

    public TestClassExecutionEventGenerator(TestResultProcessor resultProcessor, IdGenerator<?> idGenerator, Clock clock) {
        this.resultProcessor = resultProcessor;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public void testClassStarted(String testClassName) {
        currentTestClass = new DefaultTestClassDescriptor(idGenerator.generateId(), testClassName, classDisplayName(testClassName));
        resultProcessor.started(currentTestClass, new TestStartEvent(clock.getCurrentTime()));
    }

    @Override
    public void testClassFinished(TestFailure failure) {
        long now = clock.getCurrentTime();
        try {
            if (failure != null) {
                if (currentTests.isEmpty()) {
                    String testName = testsStarted ? "executionError": "initializationError";
                    DefaultTestDescriptor initializationError = new DefaultTestDescriptor(idGenerator.generateId(), currentTestClass.getClassName(), testName);
                    resultProcessor.started(initializationError, new TestStartEvent(now));
                    resultProcessor.failure(initializationError.getId(), failure);
                    resultProcessor.completed(initializationError.getId(), new TestCompleteEvent(now));
                } else {
                    for (Object test : currentTests) {
                        resultProcessor.failure(test, failure);
                        resultProcessor.completed(test, new TestCompleteEvent(now));
                    }
                }
            }
            resultProcessor.completed(currentTestClass.getId(), new TestCompleteEvent(now));
        } finally {
            testsStarted = false;
            currentTests.clear();
            currentTestClass = null;
        }
    }

    @Override
    public void started(TestDescriptorInternal test, TestStartEvent event) {
        resultProcessor.started(test, event);
        testsStarted = true;
        currentTests.add(test.getId());
    }

    @Override
    public void completed(Object testId, TestCompleteEvent event) {
        currentTests.remove(testId);
        resultProcessor.completed(testId, event);
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        resultProcessor.output(testId, event);
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        resultProcessor.failure(testId, result);
    }

    //Extract class name from the fully qualified class name
    private static String classDisplayName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(lastDot + 1);
        } else {
            return className;
        }
    }
}
