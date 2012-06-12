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

import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.logging.internal.OutputEventListener

import spock.lang.Specification

class TestEventLoggerTest extends Specification {
    def outputListener = Mock(OutputEventListener)
    def testLogging = new DefaultTestLogging()
    def exceptionFormatter = Mock(TestExceptionFormatter)
    def eventLogger = new TestEventLogger(outputListener, LogLevel.INFO, testLogging, exceptionFormatter)

    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def result = new SimpleTestResult()

    def "logs event if event type matches"() {
        testLogging.events(TestLogEvent.PASSED, TestLogEvent.SKIPPED)

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        1 * outputListener.onOutput(_)

        when:
        result.resultType = TestResult.ResultType.FAILURE
        eventLogger.afterTest(methodDescriptor, result)

        then:
        0 * _
    }

    def "logs event if granularity matches"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity 2
        testLogging.maxGranularity 4

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)
        eventLogger.afterSuite(innerSuiteDescriptor, result)
        eventLogger.afterSuite(classDescriptor, result)

        then:
        3 * outputListener.onOutput(_)

        when:
        eventLogger.afterSuite(rootDescriptor, result)
        eventLogger.afterSuite(workerDescriptor, result)
        eventLogger.afterTest(methodDescriptor, result)

        then:
        0 * _
    }

    def "shows exceptions if configured"() {
        testLogging.events(TestLogEvent.FAILED)
        testLogging.showExceptions = true

        result.resultType = TestResult.ResultType.FAILURE
        result.exceptions = [new RuntimeException()]

        def outputEvent

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        1 * exceptionFormatter.format(*_) >> "formatted exception"
        1 * outputListener.onOutput({ outputEvent = it })
        outputEvent.toString().contains("formatted exception")

        when:
        testLogging.showExceptions = false
        eventLogger.afterTest(methodDescriptor, result)

        then:
        0 * exceptionFormatter.format(*_)
    }
}
