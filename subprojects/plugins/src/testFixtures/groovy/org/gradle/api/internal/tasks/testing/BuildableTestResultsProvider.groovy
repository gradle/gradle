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
    Map<Long, BuildableTestClassResult> testClasses = [:]
    long idCounter = 1

    BuildableTestClassResult testClassResult(String className, Closure configClosure = {}) {
        BuildableTestClassResult testSuite = new BuildableTestClassResult(idCounter++, className, timestamp)
        testSuite.with(configClosure)
        testClasses[testSuite.id] = testSuite
    }

    void writeAllOutput(long id, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(id, 0, true, destination, writer)
    }

    void writeNonTestOutput(long id, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(id, 0, false, destination, writer)
    }

    void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(classId, testId, false, destination, writer)
    }

    void doWrite(long classId, long testId, boolean allClassOutput, TestOutputEvent.Destination destination, Writer writer) {
        BuildableTestClassResult testCase = testClasses[classId]
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

    boolean isHasResults() {
        !testClasses.isEmpty()
    }

    boolean hasOutput(long classId, TestOutputEvent.Destination destination) {
        testClasses[classId]?.outputEvents?.find { it.testOutputEvent.destination == destination }
    }

    static class BuildableOutputEvent {
        long testId
        TestOutputEvent testOutputEvent

        BuildableOutputEvent(long testId, TestOutputEvent testOutputEvent) {
            this.testId = testId
            this.testOutputEvent = testOutputEvent
        }
    }

    class BuildableTestClassResult extends TestClassResult {
        List<BuildableOutputEvent> outputEvents = []

        long duration = 1000

        BuildableTestClassResult(long id, String className, long startTime) {
            super(id, className, startTime)
        }

        BuildableTestMethodResult testcase(String name, Closure configClosure = {}) {
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(idCounter++, name, outputEvents, new SimpleTestResult())
            add(methodResult)
            ConfigureUtil.configure(configClosure, methodResult)
        }

        BuildableTestMethodResult testcase(long id, String name, Closure configClosure = {}) {
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(id, name, outputEvents, new SimpleTestResult())
            add(methodResult)
            ConfigureUtil.configure(configClosure, methodResult)
        }

        def stderr(String output) {
            outputEvents << new BuildableOutputEvent(0, new DefaultTestOutputEvent(StdErr, output))
        }

        def stdout(String output) {
            outputEvents << new BuildableOutputEvent(0, new DefaultTestOutputEvent(StdOut, output))
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

        BuildableTestMethodResult(long id, String name, List<BuildableOutputEvent> outputEvents, TestResult result) {
            super(id, name, result)
            this.outputEvents = outputEvents
            duration = result.endTime - result.startTime;
        }

        void failure(String message, String text) {
            exceptions.add(new TestResultException(message, text))
        }

        def stderr(String output) {
            outputEvents << new BuildableOutputEvent(getId(), new DefaultTestOutputEvent(StdErr, output))
        }

        def stdout(String output) {
            outputEvents << new BuildableOutputEvent(getId(), new DefaultTestOutputEvent(StdOut, output))
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



