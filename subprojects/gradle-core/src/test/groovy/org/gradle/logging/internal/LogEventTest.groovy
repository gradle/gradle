/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal

import spock.lang.Specification
import org.gradle.api.logging.LogLevel
import org.gradle.logging.StyledTextOutput

class LogEventTest extends Specification {
    private final StyledTextOutput output = Mock()

    def renderWritesMessageToTextOutput() {
        when:
        new LogEvent(0, 'category', LogLevel.INFO, 'message', null).render(output)

        then:
        1 * output.text('message')
        1 * output.println()
        0 * output._
    }
    
    def renderWritesMessageAndExceptionToTextOutput() {
        def failure = new RuntimeException()

        when:
        new LogEvent(0, 'category', LogLevel.INFO, 'message', failure).render(output)

        then:
        1 * output.text('message')
        1 * output.println()
        1 * output.exception(failure)
        0 * output._
    }
}
