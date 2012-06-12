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

import spock.lang.Specification
import org.gradle.logging.internal.OutputEventListener
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.logging.internal.OutputEvent

class AbstractTestLoggerTest extends Specification {
    OutputEventListener listener = Mock()
    OutputEvent outputEvent
    AbstractTestLogger logger

    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def setup() {
        _ * listener.onOutput({ outputEvent = it })
    }

    def "log started outer suite event"() {
        createLogger(LogLevel.ERROR)

        when:
        logger.logEvent(outerSuiteDescriptor, TestLogEvent.STARTED)

        then:
        outputEvent.toString() == "[ERROR] [testLogging] <Normal>com.OuterSuiteClass </Normal><Normal>STARTED</Normal><Normal>\n</Normal>"
    }

    def "log passed inner suite event"() {
        createLogger(LogLevel.QUIET)

        when:
        logger.logEvent(innerSuiteDescriptor, TestLogEvent.PASSED)

        then:
        outputEvent.toString() == "[QUIET] [testLogging] <Normal>com.OuterSuiteClass > com.InnerSuiteClass </Normal><Identifier>PASSED</Identifier><Normal>\n</Normal>"

    }

    def "log skipped test class event"() {
        createLogger(LogLevel.WARN)

        when:
        logger.logEvent(classDescriptor, TestLogEvent.SKIPPED)

        then:
        outputEvent.toString() == "[WARN] [testLogging] <Normal>com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass </Normal><Info>SKIPPED</Info><Normal>\n</Normal>"
    }

    def "log failed test method event"() {
        createLogger(LogLevel.LIFECYCLE)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.FAILED)

        then:
        outputEvent.toString() == "[LIFECYCLE] [testLogging] <Normal>com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod </Normal><Failure>FAILED</Failure><Normal>\n</Normal>"
    }

    def "log standard out event with details"() {
        createLogger(LogLevel.INFO)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_OUT, "this is a\nstandard out\nevent")

        then:
        outputEvent.toString() == "[INFO] [testLogging] <Normal>com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod " +
                "</Normal><Normal>STANDARD_OUT</Normal><Normal>\nthis is a\nstandard out\nevent\n</Normal>"
    }

    def "log standard error event with details"() {
        createLogger(LogLevel.DEBUG)

        when:
        logger.logEvent(methodDescriptor, TestLogEvent.STANDARD_ERROR, "this is a\nstandard error\nevent")

        then:
        outputEvent.toString() == "[DEBUG] [testLogging] <Normal>com.OuterSuiteClass > com.InnerSuiteClass > foo.bar.TestClass > testMethod " +
                "</Normal><Normal>STANDARD_ERROR</Normal><Normal>\nthis is a\nstandard error\nevent\n</Normal>"
    }

    def createLogger(LogLevel level) {
        logger = new AbstractTestLogger(listener, level) {}
    }
}
