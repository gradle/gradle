/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.events

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

class LogEventTest extends Specification {
    public static final long TIMESTAMP = 0L
    public static final String CATEGORY = 'category'
    public static final String MESSAGE = 'message'
    private final StyledTextOutput output = Mock()

    def renderWritesMessageToTextOutput() {
        given:
        def logEvent = new LogEvent(TIMESTAMP, CATEGORY, LogLevel.INFO, MESSAGE, null)

        when:
        logEvent.render(output)

        then:
        1 * output.text(MESSAGE)
        1 * output.println()
        TIMESTAMP * output._
    }

    def renderWritesMessageAndExceptionToTextOutput() {
        given:
        def failure = new RuntimeException()
        def logEvent = new LogEvent(TIMESTAMP, CATEGORY, LogLevel.INFO, MESSAGE, failure)

        when:
        logEvent.render(output)

        then:
        1 * output.text(MESSAGE)
        1 * output.println()
        1 * output.exception(failure)
        0 * output._
    }
}
