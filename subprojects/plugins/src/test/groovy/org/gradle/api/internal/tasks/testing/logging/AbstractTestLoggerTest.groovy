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
import org.gradle.logging.StyledTextOutputFactory
import org.gradle.logging.TestStyledTextOutputFactory

import spock.lang.Specification
import org.gradle.util.TextUtil

class AbstractTestLoggerTest extends Specification {
    StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    AbstractTestLogger logger
    def sep = TextUtil.platformLineSeparator

    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def "log started outer suite event"() {
        createLogger(LogLevel.ERROR)

        when:
        logger.logEvent(outerSuiteDescriptor, TestLogEvent.STARTED)

        then:
        textOutputFactory.toString() == "{TestLogger}{ERROR}com.OuterSuiteClass STARTED${sep}"
    }

    def "log passed inner suite event"() {
        createLogger(LogLevel.QUIET)

        when:
        logger.logEvent(innerSuiteDescriptor, TestLogEvent.PASSED)

        then:
        textOutputFactory.toString() == "{TestLogger}{QUIET}com.OuterSuiteClass > com.InnerSuiteClass {identifier}PASSED{normal}$sep"

    }

    def "log skipped test class event"() {
        createLogger(LogLevel.WARN)

        when:
        logger.logEvent(classDescriptor, TestLogEvent.SKIPPED)

        then:
        textOutputFactory.toString() == "{TestLogger}{WARN}com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass {info}SKIPPED{normal}${sep}"
    }

    def "log failed test method event"() {
        createLogger(LogLevel.LIFECYCLE)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.FAILED)

        then:
        textOutputFactory.toString() == "{TestLogger}{LIFECYCLE}com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod {failure}FAILED{normal}${sep}"
    }

    def "log standard out event with details"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_OUT, "this is a${sep}standard out${sep}event")

        then:
        textOutputFactory.toString() == "{TestLogger}{INFO}com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod STANDARD_OUT${sep}this is a${sep}standard out${sep}event${sep}"
    }

    def "log standard error event with details"() {
        createLogger(LogLevel.DEBUG)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_ERROR, "this is a${sep}standard error${sep}event")

        then:
        textOutputFactory.toString() == "{TestLogger}{DEBUG}com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod STANDARD_ERROR${sep}this is a${sep}standard error${sep}event${sep}"
    }

    def createLogger(LogLevel level) {
        logger = new AbstractTestLogger(textOutputFactory, level) {}
    }
}
