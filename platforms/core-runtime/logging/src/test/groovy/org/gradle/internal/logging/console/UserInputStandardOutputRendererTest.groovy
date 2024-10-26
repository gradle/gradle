/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.time.TestTime.timestampOf

class UserInputStandardOutputRendererTest  extends Specification {
    def listener = Mock(OutputEventListener)
    def userInput = Mock(GlobalUserInputReceiver)
    @Subject def renderer = new UserInputStandardOutputRenderer(listener, userInput)

    def "can handle user input request and resume events"() {
        given:
        def userInputRequestEvent = new UserInputRequestEvent()
        def userInputResumeEvent = new UserInputResumeEvent(timestampOf(123))

        when:
        renderer.onOutput(userInputRequestEvent)

        then:
        renderer.eventQueue.empty

        when:
        renderer.onOutput(userInputResumeEvent)

        then:
        1 * listener.onOutput(userInputResumeEvent)
        0 * listener.onOutput(_)
        0 * userInput._
        renderer.eventQueue.empty
    }

    def "throws exception if user input resume event has been received but event handling hasn't been paused"() {
        given:
        def event = new UserInputResumeEvent(timestampOf(123))

        when:
        renderer.onOutput(event)

        then:
        def t = thrown(IllegalStateException)
        t.message == 'Cannot resume user input if not paused yet'
        0 * listener.onOutput(_)
        0 * userInput._
        renderer.eventQueue.empty
    }

    def "can replay queued events if event handling is paused"() {
        given:
        def userInputRequestEvent = new UserInputRequestEvent()
        def userInputResumeEvent = new UserInputResumeEvent(timestampOf(123))

        when:
        renderer.onOutput(userInputRequestEvent)

        then:
        renderer.eventQueue.empty

        when:
        def testOutputEvent1 = new TestOutputEvent()
        def testOutputEvent2 = new TestOutputEvent()
        renderer.onOutput(testOutputEvent1)
        renderer.onOutput(testOutputEvent2)

        then:
        0 * listener.onOutput(_)
        0 * userInput._
        renderer.eventQueue.size() == 2

        when:
        renderer.onOutput(userInputResumeEvent)

        then:
        1 * listener.onOutput(userInputResumeEvent)
        1 * listener.onOutput(testOutputEvent1)
        1 * listener.onOutput(testOutputEvent2)
        0 * listener.onOutput(_)
        0 * userInput._
        renderer.eventQueue.empty
    }

    def "passes through other events than user input events"() {
        given:
        def testOutputEvent = new TestOutputEvent()

        when:
        renderer.onOutput(testOutputEvent)

        then:
        1 * listener.onOutput(testOutputEvent)
        0 * userInput._
        renderer.eventQueue.empty
    }

    private static class TestOutputEvent extends OutputEvent {
        @Override
        LogLevel getLogLevel() {
            return null
        }
    }
}
