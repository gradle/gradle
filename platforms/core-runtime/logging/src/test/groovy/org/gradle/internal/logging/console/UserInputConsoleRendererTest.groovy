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
import org.gradle.internal.logging.events.PromptOutputEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import spock.lang.Specification
import spock.lang.Subject

class UserInputConsoleRendererTest extends Specification {

    def listener = Mock(OutputEventListener)
    def console = Mock(Console)
    def buildProgressArea = Mock(BuildProgressArea)
    def textArea = Mock(TextArea)
    def userInput = Mock(GlobalUserInputReceiver)
    @Subject def renderer = new UserInputConsoleRenderer(listener, console, userInput)

    def "can handle user input request and resume events"() {
        given:
        def prompt = Mock(PromptOutputEvent)
        def userInputRequestEvent = new UserInputRequestEvent()
        def userInputResumeEvent = new UserInputResumeEvent(123)

        when:
        renderer.onOutput(userInputRequestEvent)

        then:
        1 * console.getBuildProgressArea() >> buildProgressArea
        1 * buildProgressArea.setVisible(false)
        1 * console.flush()

        and:
        0 * console._
        0 * listener.onOutput(_)
        0 * prompt._
        0 * textArea._
        0 * userInput._
        renderer.eventQueue.empty

        when:
        renderer.onOutput(prompt)

        then:
        1 * console.getBuildOutputArea() >> textArea
        1 * prompt.render(textArea)
        1 * console.flush()
        1 * userInput.readAndForwardText(prompt)

        and:
        0 * console._
        0 * listener.onOutput(_)
        0 * prompt._
        0 * textArea._
        0 * userInput._
        renderer.eventQueue.empty

        when:
        renderer.onOutput(userInputResumeEvent)

        then:
        1 * console.getBuildOutputArea() >> textArea
        1 * textArea.println()
        1 * console.getBuildProgressArea() >> buildProgressArea
        1 * buildProgressArea.setVisible(true)
        1 * console.flush()

        and:
        0 * console._
        0 * listener.onOutput(_)
        0 * prompt._
        0 * textArea._
        0 * userInput._
        renderer.eventQueue.empty
    }

    def "throws exception if user input resume event has been received but event handling hasn't been paused"() {
        given:
        def event = new UserInputResumeEvent(123)

        when:
        renderer.onOutput(event)

        then:
        def t = thrown(IllegalStateException)
        t.message == 'Cannot resume user input if not paused yet'
        0 * console._
        0 * listener.onOutput(_)
        renderer.eventQueue.empty
    }

    def "can replay queued events if event handling is paused"() {
        given:
        def userInputRequestEvent = new UserInputRequestEvent()
        def userInputResumeEvent = new UserInputResumeEvent(123)

        when:
        renderer.onOutput(userInputRequestEvent)

        then:
        1 * console.getBuildProgressArea() >> buildProgressArea
        1 * buildProgressArea.setVisible(false)
        1 * console.flush()
        0 * console._
        0 * listener.onOutput(_)
        renderer.eventQueue.empty

        when:
        def testOutputEvent1 = new TestOutputEvent()
        def testOutputEvent2 = new TestOutputEvent()
        renderer.onOutput(testOutputEvent1)
        renderer.onOutput(testOutputEvent2)

        then:
        0 * console._
        0 * listener.onOutput(_)
        renderer.eventQueue.size() == 2

        when:
        renderer.onOutput(userInputResumeEvent)

        then:
        1 * console.buildOutputArea >> textArea
        1 * console.getBuildProgressArea() >> buildProgressArea
        1 * buildProgressArea.setVisible(true)
        1 * console.flush()
        0 * console._
        1 * listener.onOutput(testOutputEvent1)
        1 * listener.onOutput(testOutputEvent2)
        renderer.eventQueue.empty
    }

    def "passes through other events than user input events"() {
        given:
        def testOutputEvent = new TestOutputEvent()

        when:
        renderer.onOutput(testOutputEvent)

        then:
        0 * console._
        1 * listener.onOutput(testOutputEvent)
        renderer.eventQueue.empty
    }

    private static class TestOutputEvent extends OutputEvent {
        @Override
        LogLevel getLogLevel() {
            return null
        }
    }
}
