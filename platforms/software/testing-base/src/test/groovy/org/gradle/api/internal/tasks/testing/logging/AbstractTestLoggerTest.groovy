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
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import spock.lang.Specification

class AbstractTestLoggerTest extends Specification {
    StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    AbstractTestLogger logger

    def rootDescriptor = new SimpleTestDescriptor(displayName: "Test Run", className: null, composite: true)
    def workerDescriptor = new SimpleTestDescriptor(displayName: "Gradle Worker 2", className: null, composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", displayName: "OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", displayName: "InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", displayName: "TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", displayName: "a test", className: "foo.bar.TestClass", parent: classDescriptor)

    def "log test run event"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(rootDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.INFO
        textOutputFactory.output == """
Test Run STARTED
"""
    }

    def "log Gradle worker event"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(workerDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.INFO
        textOutputFactory.output == """
Gradle Worker 2 STARTED
"""
    }

    def "log outer suite event"() {
        createLogger(LogLevel.ERROR)

        when:
        logger.logEvent(outerSuiteDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.ERROR
        textOutputFactory.output == """
OuterSuiteClass STARTED
"""
    }

    def "log inner suite event"() {
        createLogger(LogLevel.QUIET)

        when:
        logger.logEvent(innerSuiteDescriptor, TestLogEvent.PASSED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.QUIET
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass {identifier}PASSED{normal}
"""

    }

    def "log test class event"() {
        createLogger(LogLevel.WARN)

        when:
        logger.logEvent(classDescriptor, TestLogEvent.SKIPPED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.WARN
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass > TestClass {info}SKIPPED{normal}
"""
    }

    def "log test method event"() {
        createLogger(LogLevel.LIFECYCLE)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.FAILED)

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.LIFECYCLE
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass > TestClass > a test {failure}FAILED{normal}
"""
    }

    def "log standard out event"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_OUT, """this is a
standard out
event""")


        then:
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass > TestClass > a test STANDARD_OUT
this is a
standard out
event"""
    }

    def "log standard error event"() {
        createLogger(LogLevel.DEBUG)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_ERROR, """this is a
standard error
event""")

        then:
        textOutputFactory.category == "TestEventLogger"
        textOutputFactory.logLevel == LogLevel.DEBUG
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass > TestClass > a test STANDARD_ERROR
this is a
standard error
event"""

    }

    def "log test method event with lowest display granularity"() {
        createLogger(LogLevel.INFO, 0)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.FAILED)

        then:
        textOutputFactory.output == """
Test Run > Gradle Worker 2 > OuterSuiteClass > InnerSuiteClass > TestClass > a test {failure}FAILED{normal}
"""
    }

    def "log test method event with highest display granularity"() {
        createLogger(LogLevel.INFO, -1)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.FAILED)

        then:
        textOutputFactory.output == """
a test {failure}FAILED{normal}
"""
    }

    def "logging of atomic test whose ancestors don't have a test class"() {
        createLogger(LogLevel.INFO)
        def testSuiteDescriptor = new SimpleTestDescriptor(name: "Tests", displayName: "Tests", className: null, composite: true, parent: workerDescriptor)
        methodDescriptor.parent = testSuiteDescriptor

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.output == """
Tests > foo.bar.TestClass.a test STARTED
"""
    }

    def "logging of atomic test whose parent is a method"() {
        createLogger(LogLevel.INFO)
        def dynamicTestGeneratorDescriptor = new SimpleTestDescriptor(name: "streamOfTests()", displayName: "streamOfTests()", className: null, composite: true, parent: outerSuiteDescriptor)
        methodDescriptor.parent = dynamicTestGeneratorDescriptor

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.output == """
OuterSuiteClass > streamOfTests() > a test STARTED
"""
    }

    def "logging of orphan atomic test"() {
        createLogger(LogLevel.INFO)
        methodDescriptor.parent = null

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.output == """
foo.bar.TestClass.a test STARTED
"""
    }

    def "logs header just once per batch of events with same type and for same test"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_OUT, "event 1")
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_OUT, "event 2")

        then:
        textOutputFactory.output == """
OuterSuiteClass > InnerSuiteClass > TestClass > a test STANDARD_OUT
event 1event 2"""
    }

    void createLogger(LogLevel level, int displayGranularity = 2) {
        logger = new AbstractTestLogger(textOutputFactory, level, displayGranularity) {}
    }
}
