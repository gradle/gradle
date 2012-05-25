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
import org.gradle.logging.internal.OutputEventListener

import spock.lang.Specification

class TestEventLoggerTest extends Specification {
    def outputListener = Mock(OutputEventListener)
    def testLogging = new DefaultTestLogging()
    def exceptionFormatter = Mock(TestExceptionFormatter)
    def eventLogger = new TestEventLogger(outputListener, LogLevel.INFO, testLogging, exceptionFormatter)
    def descriptor = new SimpleTestDescriptor()
    def result = new SimpleTestResult()

    def "logs event if granularity and event type match"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity 0

        when:
        eventLogger.after(descriptor, result)

        then:
        1 * outputListener.onOutput({containsText(it, "PASSED")})
    }

    private boolean containsText(event, text) {
        event.spans.find { it.text.contains(text) }
    }
}
