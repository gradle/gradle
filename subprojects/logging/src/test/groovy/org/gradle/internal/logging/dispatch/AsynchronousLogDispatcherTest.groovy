/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.dispatch

import org.gradle.internal.logging.events.FlushOutputEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.test.fixtures.concurrent.ConcurrentSpec


class AsynchronousLogDispatcherTest extends ConcurrentSpec {
    def listener = Mock(OutputEventListener)
    def dispatcher = new AsynchronousLogDispatcher(listener)

    def "delivers events asynchronously in the order submitted"() {
        def event1 = Stub(OutputEvent)
        def event2 = Stub(OutputEvent)
        def event3 = Stub(OutputEvent)

        when:
        dispatcher.start()
        async {
            dispatcher.submit(event1)
            thread.blockUntil.event1
            dispatcher.submit(event2)
            dispatcher.submit(event3)
            instant.submitted
            thread.blockUntil.event3
        }

        then:
        1 * listener.onOutput(event1) >> {
            instant.event1
            thread.blockUntil.submitted
        }

        then:
        1 * listener.onOutput(event2)

        then:
        1 * listener.onOutput(event3) >> {
            instant.event3
        }
    }

    def "flush blocks until previous events handled"() {
        def event1 = Stub(OutputEvent)
        def event2 = Stub(OutputEvent)
        def flushEvent = new FlushOutputEvent()

        when:
        dispatcher.start()
        async {
            dispatcher.submit(event1)
            dispatcher.submit(event2)
            dispatcher.submit(flushEvent)
            flushEvent.waitUntilHandled()
            instant.flushComplete
        }

        then:
        instant.flushComplete > instant.handled
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2) >> {
            thread.block()
            instant.handled
        }
        1 * listener.onOutput(flushEvent)
    }

    def "collects event handling failure and forwards on flush"() {
        def event1 = Stub(OutputEvent)
        def event2 = Stub(OutputEvent)
        def failure = new RuntimeException()
        def flushEvent = new FlushOutputEvent()

        when:
        dispatcher.start()
        async {
            dispatcher.submit(event1)
            dispatcher.submit(event2)
            dispatcher.submit(flushEvent)
            flushEvent.waitUntilHandled()
        }

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * listener.onOutput(event1) >> { throw failure }
        1 * listener.onOutput(event2)
        1 * listener.onOutput(flushEvent)
    }

    def "forwards flush handling failure"() {
        def event1 = Stub(OutputEvent)
        def event2 = Stub(OutputEvent)
        def failure = new RuntimeException()
        def flushEvent = new FlushOutputEvent()

        when:
        dispatcher.start()
        async {
            dispatcher.submit(event1)
            dispatcher.submit(event2)
            dispatcher.submit(flushEvent)
            flushEvent.waitUntilHandled()
        }

        then:
        def e = thrown(RuntimeException)
        e == failure
        1 * listener.onOutput(event1)
        1 * listener.onOutput(event2)
        1 * listener.onOutput(flushEvent) >> { throw failure }
    }
}
