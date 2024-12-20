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
package org.gradle.internal.logging.text

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.services.LoggingBackedStyledTextOutput
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.DefaultBuildOperationRef
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.time.Clock
import org.gradle.internal.time.FixedClock

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput

class LoggingBackedStyledTextOutputTest extends OutputSpecification {
    private static final long NOW = 1200L

    private final OutputEventListener listener = Mock()
    private final Clock timeProvider = FixedClock.createAt(NOW)
    private final LoggingBackedStyledTextOutput output = new LoggingBackedStyledTextOutput(listener, 'category', LogLevel.INFO, timeProvider)
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance()

    def forwardsLineOfTextToListener() {
        when:
        output.println('message')

        then:
        1 * listener.onOutput({ it.spans[0].text == toNative('message\n') })
        0 * listener._
    }

    def fillsInDetailsOfEvent() {
        given:
        currentBuildOperationRef.set(new DefaultBuildOperationRef(
            new OperationIdentifier(42),
            new OperationIdentifier(1)
        ))

        when:
        output.text('message').println()

        then:
        1 * listener.onOutput(!null) >> { args ->
            def event = args[0]
            assert event.spans[0].style == Normal
            assert event.category == 'category'
            assert event.logLevel == LogLevel.INFO
            assert event.timestamp == NOW
            assert event.spans[0].text == toNative('message\n')
            assert event.buildOperationId.id == 42L
        }
        0 * listener._

        cleanup:
        currentBuildOperationRef.clear()
    }

    def buffersTextUntilEndOfLineReached() {
        when:
        output.text('message ')

        then:
        0 * listener._

        when:
        output.text(toNative('with more\nanother '))

        then:
        1 * listener.onOutput({ it.spans[0].text == toNative('message with more\n') })
        0 * listener._

        when:
        output.text('line').println()

        then:
        1 * listener.onOutput({ it.spans[0].text == toNative('another line\n') })
        0 * listener._
    }

    def forwardsEachLineOfTextToListener() {
        when:
        output.text(toNative('message1\nmessage2')).println()

        then:
        1 * listener.onOutput({ it.spans[0].text == toNative('message1\n') })
        1 * listener.onOutput({ it.spans[0].text == toNative('message2\n') })
        0 * listener._
    }

    def forwardsEmptyLinesToListener() {
        when:
        output.text(toNative('\n\n'))

        then:
        2 * listener.onOutput({ it.spans[0].text == toNative('\n') })
        0 * listener._
    }

    def canChangeTheStyle() {
        when:
        output.style(Header)
        output.println('message')

        then:
        1 * listener.onOutput(!null) >> { args ->
            def event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].style == Header
            assert event.spans[0].text == toNative('message\n')
        }
        0 * listener._
    }

    def canChangeTheStyleInsideALine() {
        when:
        output.style(Header)
        output.text('header')
        output.style(Normal)
        output.text('normal')
        output.println()

        then:
        1 * listener.onOutput(!null) >> { args ->
            def event = args[0]
            assert event.spans.size() == 2
            assert event.spans[0].style == Header
            assert event.spans[0].text == 'header'
            assert event.spans[1].style == Normal
            assert event.spans[1].text == toNative('normal\n')
        }
        0 * listener._
    }

    def ignoresEmptySpans() {
        when:
        output.style(Header)
        output.text('')
        output.style(Normal)
        output.style(UserInput)
        output.println()

        then:
        1 * listener.onOutput(!null) >> { args ->
            def event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].style == UserInput
            assert event.spans[0].text == toNative('\n')
        }
        0 * listener._
    }
}
