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
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.UpdateNowEvent
import org.gradle.util.MockExecutor
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class ThrottlingOutputEventListenerTest extends OutputSpecification {
    public static final int EVENT_QUEUE_FLUSH_PERIOD_MS = 100
    def listener = Mock(OutputEventListener)
    def timeProvider = new MockTimeProvider()
    def executor = new MockExecutor()

    @Subject renderer = new ThrottlingOutputEventListener(listener, EVENT_QUEUE_FLUSH_PERIOD_MS, executor, timeProvider)

    def "forwards events to listener after update event"() {
        def event = event('message')
        def updateNowEvent = new UpdateNowEvent(timeProvider.currentTime)

        when:
        renderer.onOutput(event)

        then:
        0 * _

        when:
        renderer.onOutput(updateNowEvent)

        then:
        1 * listener.onOutput(event)
        1 * listener.onOutput(updateNowEvent)
        0 * _
    }

    def "generates update now events periodically"() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')

        when:
        renderer.onOutput(event1)

        then:
        0 * _

        when:
        timeProvider.increment(EVENT_QUEUE_FLUSH_PERIOD_MS * 2)
        renderer.onOutput(event2)
        flushFixedScheduledActions()

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(_ as UpdateNowEvent)
        0 * _
    }

    def forwardsQueuedEventsOnEndOfOutputEvent() {
        def event1 = event('1')
        def event2 = event('2')
        def end = new EndOutputEvent()

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)

        then:
        0 * _

        when:
        renderer.onOutput(end)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(end)
        0 * _
    }

    def backgroundFlushDoesNothingWhenEventsAlreadyFlushed() {
        def event1 = event('1')
        def event2 = event('2')
        def end = new EndOutputEvent()

        given:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(end)

        when:
        flushSingleScheduledActions()

        then:
        0 * _
    }

    def "executor emits update now event when executing"() {
        when:
        flushFixedScheduledActions()

        then:
        1 * listener.onOutput(_ as UpdateNowEvent)
    }

    def "shuts down executor when receiving end output event"() {
        expect:
        !executor.isShutdown()

        when:
        renderer.onOutput(new EndOutputEvent())

        then:
        executor.isShutdown()
    }

    private void flushSingleScheduledActions() {
        executor.runSingleScheduledActionsNow()
    }

    private void flushFixedScheduledActions() {
        executor.runFixedScheduledActionsNow()
    }
}
