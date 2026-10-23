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
import spock.lang.Issue
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

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "holds the first clock event after held events, at most once per second, and replays it after them"() {
        given:
        def replayed = []
        listener.onOutput(_) >> { OutputEvent e -> replayed << e }
        def event1 = new TestOutputEvent()
        def event2 = new TestOutputEvent()
        def event3 = new TestOutputEvent()
        def heldClockEvent1 = new UpdateNowEvent(100)
        def heldClockEvent2 = new UpdateNowEvent(1_100)
        def resume = new UserInputResumeEvent(123)

        when:
        renderer.onOutput(new UserInputRequestEvent())
        // No event held before this clock event
        renderer.onOutput(new UpdateNowEvent(0))
        renderer.onOutput(event1)
        renderer.onOutput(heldClockEvent1)
        // No event held since the last held clock event
        renderer.onOutput(new UpdateNowEvent(200))
        renderer.onOutput(event2)
        // Less than 1 s after the last held clock event
        renderer.onOutput(new UpdateNowEvent(1_000))
        renderer.onOutput(heldClockEvent2)
        renderer.onOutput(event3)
        renderer.onOutput(resume)

        then:
        replayed == [resume, event1, heldClockEvent1, event2, heldClockEvent2, event3]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "holds the first clock event of a prompt only when events received before the prompt wait for one"() {
        given:
        def received = []
        listener.onOutput(_) >> { OutputEvent e -> received << e }
        def event1 = new TestOutputEvent()
        def event2 = new TestOutputEvent()
        def heldClockEvent = new UpdateNowEvent(100)
        def liveClockEvent = new UpdateNowEvent(200)
        def resume1 = new UserInputResumeEvent(123)
        def resume2 = new UserInputResumeEvent(456)

        when:
        renderer.onOutput(event1)
        renderer.onOutput(new UserInputRequestEvent())
        renderer.onOutput(heldClockEvent)
        renderer.onOutput(resume1)
        renderer.onOutput(event2)
        renderer.onOutput(liveClockEvent)
        renderer.onOutput(new UserInputRequestEvent())
        renderer.onOutput(new UpdateNowEvent(300))
        renderer.onOutput(resume2)

        then:
        received == [event1, resume1, heldClockEvent, event2, liveClockEvent, resume2]
    }

    @Issue("https://github.com/gradle/gradle/issues/39559")
    def "replays held clock events in order with the events written to disk"() {
        given:
        def replayed = []
        listener.onOutput(_) >> { OutputEvent e -> replayed << e }
        def events = (1..AbstractUserInputRenderer.MEMORY_QUEUE_LIMIT).collect {
            new LogEvent(it * 1_000, "cat", LogLevel.LIFECYCLE, "message $it", null)
        }

        when:
        renderer.onOutput(new UserInputRequestEvent())
        events.each {
            renderer.onOutput(it)
            renderer.onOutput(new UpdateNowEvent(it.timestamp))
        }

        then:
        hasOverflowFile()

        when:
        renderer.onOutput(new UserInputResumeEvent(123))

        then:
        replayed.findAll { !(it instanceof UserInputResumeEvent) }.collect { it instanceof UpdateNowEvent ? it.timestamp : it.message } == events.collectMany { [it.message, it.timestamp] }
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
            createOwnerOnlyTemporaryFile(_, _) >> {
                callCount++
                if (callCount == 1) {
                    throw new IOException("disk full")
                }
                return tempFileProvider.createOwnerOnlyTemporaryFile("user-input-overflow-", ".bin")
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
            createOwnerOnlyTemporaryFile(_, _) >> { throw new IOException("disk full") }
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
