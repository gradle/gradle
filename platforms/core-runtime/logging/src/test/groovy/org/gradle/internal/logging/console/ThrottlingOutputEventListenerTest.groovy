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
package org.gradle.internal.logging.console

import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.internal.time.FixedClock
import org.gradle.internal.time.MockClock
import org.gradle.util.internal.MockExecutor
import spock.lang.Subject

class ThrottlingOutputEventListenerTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def clock = MockClock.create()
    def executor = new MockExecutor()

    @Subject renderer = new ThrottlingOutputEventListener(listener, 100, executor, clock)

    def "queues events until update event received"() {
        def event1 = event('message')
        def event2 = event('message')

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        0 * _

        when:
        executor.runFixedScheduledActionsNow()

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(_ as UpdateNowEvent)
        0 * _
    }

    def "flushes events on end of output"() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')
        def end = new EndOutputEvent()

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)

        then:
        0 * _

        when:
        renderer.onOutput(end)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(event3)
        1 * listener.onOutput(end)
        0 * _
    }

    def "flushes events on flush event"() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')
        def flush = new FlushOutputEvent()

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)

        then:
        0 * _

        when:
        renderer.onOutput(flush)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(event3)
        1 * listener.onOutput(flush)
        0 * _
    }

    def "flushes events when queue gets too big"() {
        when:
        (1..9999).each {
            renderer.onOutput(event("Event $it"))
        }

        then:
        0 * _

        when:
        renderer.onOutput(event("Event 10_000"))

        then:
        10_000 * listener.onOutput(_)
    }

    def "background flush does nothing when events already flushed"() {
        def event1 = event('1')
        def event2 = event('2')
        def flush = new FlushOutputEvent()

        given:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(flush)

        when:
        executor.runFixedScheduledActionsNow()

        then:
        1 * listener.onOutput(_ as UpdateNowEvent)
        0 * _
    }

    def "executor emits update now event when executing"() {
        when:
        executor.runFixedScheduledActionsNow()

        then:
        1 * listener.onOutput(_ as UpdateNowEvent)
        0 * _
    }

    def "shuts down executor when receiving end output event"() {
        expect:
        !executor.isShutdown()

        when:
        renderer.onOutput(new EndOutputEvent())

        then:
        executor.isShutdown()
    }

    def "throwables are not propagated out of the run method of the output loop"() {
        given:
        def executor = new MockExecutor()
        new ThrottlingOutputEventListener(listener, 100, executor, FixedClock.create())
        def checks = executor.shutdownNow()

        when:
        checks*.run()

        then:
        1 * _ >> { throw throwable }
        noExceptionThrown()

        where:
        throwable              | _
        new Exception()        | _
        new RuntimeException() | _
        new Error()            | _
    }
}
