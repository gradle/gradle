/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.tasks.testing.junit.result.TestFailure
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.internal.ConfigureUtil

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

class BuildableTestResultsProvider implements TestResultsProvider {

    long timestamp = 0
    Map<Long, BuildableTestClassResult> testClasses = [:]
    long idCounter = 1

    BuildableTestClassResult testClassResult(String className, @DelegatesTo(value = BuildableTestClassResult, strategy = Closure.DELEGATE_FIRST) Closure configClosure = {}) {
        BuildableTestClassResult testSuite = new BuildableTestClassResult(idCounter++, className, timestamp)
        testSuite.with(configClosure)
        testClasses[testSuite.id] = testSuite
    }

    void writeAllOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(classId, 0, true, destination, writer)
    }

    void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        doWrite(classId, 0, false, destination, writer)
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

    @Override
    boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        testClasses[classId]?.outputEvents?.find { it.testId == testId && it.testOutputEvent.destination == destination }
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

        Map<String, Integer> methodCounter = [:]

        BuildableTestClassResult(long id, String className, long startTime) {
            super(id, className, startTime)
        }

        BuildableTestMethodResult testcase(String name, @DelegatesTo(value = BuildableTestMethodResult, strategy = Closure.DELEGATE_FIRST) Closure configClosure = {}) {
            testcase(idCounter++, name, configClosure)
        }

        BuildableTestMethodResult testcase(long id, String name, @DelegatesTo(value = BuildableTestMethodResult, strategy = Closure.DELEGATE_FIRST) Closure configClosure = {}) {
            def duration = methodCounter.compute(name) { ignored, value ->  value == null ? 1 : value + 1 } * 1000
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(id, name, outputEvents, new SimpleTestResult(duration))
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
        List<TestFailure> failures = []

        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS

        private final List<BuildableOutputEvent> outputEvents

        BuildableTestMethodResult(long id, String name, List<BuildableOutputEvent> outputEvents, TestResult result) {
            super(id, name)
            completed(result)
            this.outputEvents = outputEvents
            duration = result.endTime - result.startTime;
        }

        void failure(String message, String stackTrace) {
            failures.add(new TestFailure(message, stackTrace, "ExceptionType"))
            resultType = TestResult.ResultType.FAILURE
        }

        void ignore() {
            resultType = TestResult.ResultType.SKIPPED
        }

        def stderr(String output) {
            outputEvents << new BuildableOutputEvent(getId(), new DefaultTestOutputEvent(StdErr, output))
        }

        def stdout(String output) {
            outputEvents << new BuildableOutputEvent(getId(), new DefaultTestOutputEvent(StdOut, output))
        }
    }

    void close() throws IOException {
        // nothing
    }
}
