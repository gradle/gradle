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

import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.SimpleTestResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import spock.lang.Shared
import spock.lang.Specification

class TestEventLoggerTest extends Specification {
    def textOutputFactory = new TestStyledTextOutputFactory()

    def testLogging = new DefaultTestLogging()
    def exceptionFormatter = Mock(TestExceptionFormatter)
    def eventLogger = new TestEventLogger(textOutputFactory, LogLevel.INFO, testLogging, exceptionFormatter)

    @Shared
    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    @Shared
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    @Shared
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    @Shared
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    @Shared
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    @Shared
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def result = new SimpleTestResult()

    def "logs event if event type matches"() {
        testLogging.events(TestLogEvent.PASSED, TestLogEvent.SKIPPED)

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.output.count("PASSED") == 1

        when:
        result.resultType = TestResult.ResultType.FAILURE
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.output.count("FAILED") == 0
    }

    def "logs event if granularity matches"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity = 2
        testLogging.maxGranularity = 4

        when:
        eventLogger.afterSuite(descriptor, result)
        then:
        textOutputFactory.output.count("PASSED") == 1

        where:
        descriptor << [outerSuiteDescriptor, innerSuiteDescriptor, classDescriptor]
    }

    def "does not log event if outside granularity"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity = 2
        testLogging.maxGranularity = 4

        when:
        eventLogger.afterSuite(descriptor, result)
        then:
        textOutputFactory.output.count("PASSED") == 0

        where:
        descriptor << [rootDescriptor, workerDescriptor,methodDescriptor]
    }

    def "shows exceptions by default"() {
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.exceptions = [new RuntimeException()]
        exceptionFormatter.format(*_) >> "formatted exception"

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.output.contains("formatted exception")
    }

    def "does not show exceptions if configured"() {
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.exceptions = [new RuntimeException()]
        exceptionFormatter.format(*_) >> "formatted exception"

        when:
        testLogging.showExceptions = false
        eventLogger.afterTest(methodDescriptor, result)

        then:
        !textOutputFactory.output.contains("formatted exception")
    }

    def "allows empty event set"() {
        expect:
        testLogging.setEvents(Collections.emptySet())
    }

    def "framework startup failure on composite descriptor bypasses default granularity"() {
        // Default granularity: minGranularity = -1 (leaves only)
        // outerSuiteDescriptor is composite, so under default granularity it would be filtered.
        def cause = new RuntimeException("init boom")
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.failures = [DefaultTestFailure.fromTestFrameworkStartupFailure(cause)]
        result.exceptions = [cause]
        exceptionFormatter.format(*_) >> "formatted startup exception"

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)

        then:
        textOutputFactory.output.count("FAILED") == 1
        textOutputFactory.output.contains("formatted startup exception")
    }

    def "regular framework failure on composite descriptor is still filtered by default granularity"() {
        // Regression: only framework-startup failures bypass, not generic framework failures.
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.failures = [DefaultTestFailure.fromTestFrameworkFailure(new RuntimeException("regular failure"), null)]
        exceptionFormatter.format(*_) >> "formatted regular exception"

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)

        then:
        textOutputFactory.output.count("FAILED") == 0
    }

    def "framework startup failure does not bypass events-set filter"() {
        // The granularity bypass must NOT also override the events-set predicate.
        // If FAILED is not in the events set, the user has explicitly silenced it.
        testLogging.setEvents(Collections.emptySet())
        result.resultType = TestResult.ResultType.FAILURE
        result.failures = [DefaultTestFailure.fromTestFrameworkStartupFailure(new RuntimeException("init boom"))]

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)

        then:
        textOutputFactory.output.count("FAILED") == 0
    }
}
