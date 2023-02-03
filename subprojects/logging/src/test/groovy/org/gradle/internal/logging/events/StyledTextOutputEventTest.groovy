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

class StyledTextOutputEventTest extends Specification {

    def canSetLogLevel() {
        def event = new StyledTextOutputEvent(100, 'category', LogLevel.DEBUG, null, 'message')

        expect:
        event.logLevel == LogLevel.DEBUG
    }

    def rendersToTextOutput() {
        StyledTextOutput output = Mock()
        List spans = [new StyledTextOutputEvent.Span(StyledTextOutput.Style.UserInput, 'message')]
        def event = new StyledTextOutputEvent(100, 'category', LogLevel.LIFECYCLE, null, spans)

        when:
        event.render(output)

        then:
        1 * output.style(StyledTextOutput.Style.UserInput)
        1 * output.text('message')
        0 * output._
    }

    def rendersMultipleSpansToTextOutput() {
        StyledTextOutput output = Mock()
        List spans = [new StyledTextOutputEvent.Span(StyledTextOutput.Style.UserInput, 'UserInput'),
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Normal, 'Normal'),
                new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, 'Header')
        ]
        def event = new StyledTextOutputEvent(100, 'category', LogLevel.LIFECYCLE, null, spans)

        when:
        event.render(output)

        then:
        1 * output.style(StyledTextOutput.Style.UserInput)
        1 * output.text('UserInput')
        1 * output.style(StyledTextOutput.Style.Normal)
        1 * output.text('Normal')
        1 * output.style(StyledTextOutput.Style.Header)
        1 * output.text('Header')
        0 * output._
    }
}
