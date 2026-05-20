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

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

class UserInputStandardOutputRendererTest extends Specification {
    @TempDir File tmpDir
    def listener = Mock(OutputEventListener)
    def userInput = Mock(GlobalUserInputReceiver)
    def tempFileProvider = new DefaultTemporaryFileProvider({ tmpDir })
    @Subject def renderer = new UserInputStandardOutputRenderer(listener, userInput, tempFileProvider)

    def "can handle user input request and resume events"() {
        given:
        def userInputRequestEvent = new UserInputRequestEvent()
        def userInputResumeEvent = new UserInputResumeEvent(123)

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
        def event = new UserInputResumeEvent(123)

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
        def userInputResumeEvent = new UserInputResumeEvent(123)

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

    def "overflows to disk and replays all events in order"() {
        given:
        def totalEvents = AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT + 500
        def events = (1..totalEvents).collect {
            new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "message $it", null)
        }

        when:
        renderer.onOutput(new UserInputRequestEvent())
        events.each { renderer.onOutput(it) }

        then:
        renderer.bufferedEventCount == totalEvents
        hasOverflowFile()

        when:
        def replayed = []
        listener.onOutput(_ as LogEvent) >> { OutputEvent e -> replayed << e }
        renderer.onOutput(new UserInputResumeEvent(123))

        then:
        replayed.size() == totalEvents
        replayed*.message == events*.message
    }

    def "filters client-local timing events while paused"() {
        given:
        renderer.onOutput(new UserInputRequestEvent())

        when:
        renderer.onOutput(new UpdateNowEvent(System.currentTimeMillis()))
        renderer.onOutput(new FlushOutputEvent())
        renderer.onOutput(new EndOutputEvent())

        then:
        renderer.bufferedEventCount == 0
    }

    def "overflow file is deleted after replay"() {
        given:
        def totalEvents = AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT + 100

        when:
        renderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            renderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "msg $it", null))
        }

        then:
        hasOverflowFile()

        when:
        renderer.onOutput(new UserInputResumeEvent(123))

        then:
        !hasOverflowFile()
    }

    def "handles multiple pause/resume cycles with overflow"() {
        given:
        def totalEvents = AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT + 200
        def replayed = []
        listener.onOutput(_ as LogEvent) >> { OutputEvent e -> replayed << e }

        when: 'first pause/resume cycle overflows to disk'
        renderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            renderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "cycle1 msg $it", null))
        }

        then:
        hasOverflowFile()

        when:
        renderer.onOutput(new UserInputResumeEvent(1))

        then:
        replayed.size() == totalEvents
        !hasOverflowFile()

        when: 'second pause/resume cycle also overflows to disk'
        replayed.clear()
        renderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            renderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "cycle2 msg $it", null))
        }

        then:
        hasOverflowFile()

        when:
        renderer.onOutput(new UserInputResumeEvent(2))

        then:
        replayed.size() == totalEvents
        !hasOverflowFile()
    }

    def "disk failure recovery across pause/resume cycles"() {
        given:
        def callCount = 0
        def unreliableProvider = Mock(TemporaryFileProvider) {
            createTemporaryFile(_, _) >> {
                callCount++
                if (callCount == 1) {
                    throw new IOException("disk full")
                }
                return tempFileProvider.createTemporaryFile("user-input-overflow-", ".bin")
            }
        }
        def testRenderer = new UserInputStandardOutputRenderer(listener, userInput, unreliableProvider)
        def totalEvents = AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT + 100

        when: 'first cycle - disk fails, falls back to in-memory'
        testRenderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            testRenderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "msg $it", null))
        }

        then: 'no overflow file because creation failed'
        !hasOverflowFile()

        when:
        testRenderer.onOutput(new UserInputResumeEvent(1))

        then:
        totalEvents * listener.onOutput(_ as LogEvent)

        when: 'second cycle - disk works again'
        testRenderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            testRenderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "msg $it", null))
        }

        then: 'overflow file exists because creation succeeded this time'
        hasOverflowFile()

        when:
        testRenderer.onOutput(new UserInputResumeEvent(2))

        then:
        totalEvents * listener.onOutput(_ as LogEvent)
        !hasOverflowFile()
    }

    def "falls back to in-memory buffer when disk overflow fails"() {
        given:
        def failingProvider = Mock(TemporaryFileProvider) {
            createTemporaryFile(_, _) >> { throw new IOException("disk full") }
        }
        def failRenderer = new UserInputStandardOutputRenderer(listener, userInput, failingProvider)
        def totalEvents = AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT + 100

        when:
        failRenderer.onOutput(new UserInputRequestEvent())
        (1..totalEvents).each {
            failRenderer.onOutput(new LogEvent(System.currentTimeMillis(), "cat", LogLevel.LIFECYCLE, "msg $it", null))
        }
        failRenderer.onOutput(new UserInputResumeEvent(123))

        then:
        1 * listener.onOutput(_ as UserInputResumeEvent)
        totalEvents * listener.onOutput(_ as LogEvent)
    }

    private boolean hasOverflowFile() {
        tmpDir.listFiles().collect { it.name.startsWith("user-input-overflow-") && it.name.endsWith(".bin") }.size() == 1
    }

    private static class TestOutputEvent extends OutputEvent {
        @Override
        LogLevel getLogLevel() {
            return null
        }
    }
}
