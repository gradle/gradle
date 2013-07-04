/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.ConfigureUtil

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

class BuildableTestResultsProvider implements TestResultsProvider {

    long timestamp = 0
    Map<String, BuildableTestClassResult> testClasses = [:]

    BuildableTestClassResult testClassResult(String className, Closure configClosure = {}) {
        BuildableTestClassResult testSuite = new BuildableTestClassResult(className, timestamp)
        testSuite.with(configClosure)
        testClasses[className] = testSuite
    }

    void writeAllOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(className, null, true, destination, writer)
    }

    void writeNonTestOutput(String className, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(className, null, false, destination, writer)
    }

    void writeTestOutput(String className, Object testId, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(className, testId, false, destination, writer)
    }

    void doWrite(String className, Object testId, boolean allClassOutput, TestOutputEvent.Destination destination, Writer writer) {
        BuildableTestClassResult testCase = testClasses[className]
        testCase.outputEvents.each { BuildableOutputEvent event ->
            if (event.testOutputEvent.destination == destination && (allClassOutput || testId == event.testId)) {
                writer.append(event.testOutputEvent.message)
            }
        }
    }

    void visitClasses(Action<? super TestClassResult> visitor) {
        testClasses.values().each {
            visitor.execute(it)
        }
    }

    boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        testClasses[className]?.outputEvents?.find { it.testOutputEvent.destination == destination }
    }

    static class BuildableOutputEvent {
        Object testId
        TestOutputEvent testOutputEvent

        BuildableOutputEvent(Object testId, TestOutputEvent testOutputEvent) {
            this.testId = testId
            this.testOutputEvent = testOutputEvent
        }
    }

    static class BuildableTestClassResult extends TestClassResult {
        List<BuildableOutputEvent> outputEvents = []

        long duration = 1000

        BuildableTestClassResult(String className, long startTime) {
            super(className, startTime)
        }

        BuildableTestMethodResult testcase(String name, Closure configClosure = {}) {
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(name, name, outputEvents, new SimpleTestResult())
            add(methodResult)
            ConfigureUtil.configure(configClosure, methodResult)
        }

        BuildableTestMethodResult testcase(Object id, String name, Closure configClosure = {}) {
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(id, name, outputEvents, new SimpleTestResult())
            add(methodResult)
            ConfigureUtil.configure(configClosure, methodResult)
        }

        def stderr(String output) {
            outputEvents << new BuildableOutputEvent(null, new DefaultTestOutputEvent(StdErr, output))
        }

        def stdout(String output) {
            outputEvents << new BuildableOutputEvent(null, new DefaultTestOutputEvent(StdOut, output))
        }

        @Override
        long getDuration() {
            this.duration
        }
    }

    static class BuildableTestMethodResult extends TestMethodResult {

        long duration
        List<Throwable> exceptions = []

        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS

        private final List<BuildableOutputEvent> outputEvents

        BuildableTestMethodResult(Object id, String name, List<BuildableOutputEvent> outputEvents, TestResult result) {
            super(id, name, result)
            this.outputEvents = outputEvents
            duration = result.endTime - result.startTime;
        }

        void failure(String message, String text) {
            exceptions.add(new TestResultException(message, text))
        }

        def stderr(String output) {
            outputEvents << new BuildableOutputEvent(name, new DefaultTestOutputEvent(StdErr, output))
        }

        def stdout(String output) {
            outputEvents << new BuildableOutputEvent(name, new DefaultTestOutputEvent(StdOut, output))
        }
    }

    static class TestResultException extends Exception {

        private final String message
        private final String text

        TestResultException(String message, String text) {
            super(message)
            this.text = text
        }

        String toString() {
            return message
        }

        public void printStackTrace(PrintWriter s) {
            s.print(text);
        }
    }
}



