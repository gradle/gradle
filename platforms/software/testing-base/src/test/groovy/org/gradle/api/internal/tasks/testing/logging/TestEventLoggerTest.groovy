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

package org.gradle.api.internal.tasks.testing.logging

import org.gradle.api.internal.tasks.testing.SimpleTestResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.logging.TestLogging
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

class TestEventLoggerTest extends Specification {
    StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()

    TestLogging testLogging = TestUtil.newInstance(DefaultTestLogging.class)
    TestExceptionFormatter exceptionFormatter = Mock(TestExceptionFormatter)

    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def result = new SimpleTestResult()

    def "logs event if event type matches"() {
        testLogging.events(TestLogEvent.PASSED, TestLogEvent.SKIPPED)
        def eventLogger = newTestEventLogger()

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 1

        when:
        textOutputFactory.clear()
        result.resultType = TestResult.ResultType.FAILURE
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 0
    }

    def "logs event if granularity matches"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity = 2
        testLogging.maxGranularity = 4
        def eventLogger = newTestEventLogger()

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)
        eventLogger.afterSuite(innerSuiteDescriptor, result)
        eventLogger.afterSuite(classDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 3

        when:
        textOutputFactory.clear()
        eventLogger.afterSuite(rootDescriptor, result)
        eventLogger.afterSuite(workerDescriptor, result)
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 0
    }

    def "shows exceptions if configured"() {
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.exceptions = [new RuntimeException()]
        exceptionFormatter.format(*_) >> "formatted exception"

        when:
        testLogging.showExceptions = true
        def eventLogger = newTestEventLogger()
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().contains("formatted exception")

        when:
        textOutputFactory.clear()
        testLogging.showExceptions = false
        eventLogger = newTestEventLogger()
        eventLogger.afterTest(methodDescriptor, result)

        then:
        !textOutputFactory.toString().contains("formatted exception")
    }

    def "allows empty event set"() {
        expect:
        testLogging.setEvents(Collections.emptySet())
    }

    def "allows standardStreams to be turned on and off"() {
        def stdOutEvent = Mock(TestOutputEvent) {
            getDestination() >> TestOutputEvent.Destination.StdOut
            getMessage() >> "Hello from StdOut"
        }
        def stdErrorEvent = Mock(TestOutputEvent) {
            getDestination() >> TestOutputEvent.Destination.StdErr
            getMessage() >> "Hello from StdErr"
        }

        when:
        textOutputFactory.clear()
        testLogging.showStandardStreams = false
        def eventLogger = newTestEventLogger()
        eventLogger.onOutput(methodDescriptor, stdOutEvent)
        eventLogger.onOutput(methodDescriptor, stdErrorEvent)

        then:
        textOutputFactory.toString().isEmpty()

        when:
        textOutputFactory.clear()
        testLogging.showStandardStreams = true
        eventLogger = newTestEventLogger()
        eventLogger.onOutput(methodDescriptor, stdOutEvent)
        eventLogger.onOutput(methodDescriptor, stdErrorEvent)

        then:
        textOutputFactory.toString().contains("Hello from StdOut")
        textOutputFactory.toString().contains("Hello from StdErr")
    }

    private TestEventLogger newTestEventLogger() {
        return new TestEventLogger(
            textOutputFactory,
            LogLevel.INFO,
            exceptionFormatter,
            testLogging.getShowExceptions().get(),
            testLogging.getMinGranularity().get(),
            testLogging.getMaxGranularity().get(),
            testLogging.getDisplayGranularity().get(),
            testLogging.getShowStandardStreams().getOrNull(),
            testLogging.getEvents().get(),
        )
    }
}
