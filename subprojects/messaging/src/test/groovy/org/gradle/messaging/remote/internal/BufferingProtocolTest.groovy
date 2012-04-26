/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal

import org.gradle.messaging.remote.internal.protocol.MessageCredits
import spock.lang.Specification

class BufferingProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final BufferingProtocol protocol = new BufferingProtocol(6)

    def setup() {
        protocol.start(context)
    }

    def "dispatches outgoing request credits on start"() {
        when:
        protocol.start(context)

        then:
        1 * context.dispatchOutgoing(new MessageCredits(6))
        0 * context._
    }

    def "queues incoming message until outgoing credits received"() {
        Message message = Mock()

        when:
        protocol.handleIncoming(message)

        then:
        0 * context._

        when:
        protocol.handleOutgoing(new MessageCredits(1))

        then:
        1 * context.dispatchIncoming(message)
        0 * context._
    }

    def "queues multiple incoming message until outgoing credits received"() {
        Message message1 = Mock()
        Message message2 = Mock()
        Message message3 = Mock()

        when:
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)
        protocol.handleIncoming(message3)

        then:
        0 * context._

        when:
        protocol.handleOutgoing(new MessageCredits(2))

        then:
        1 * context.dispatchIncoming(message1)
        1 * context.dispatchIncoming(message2)
        0 * context._
    }

    def "dispatches incoming messages until already received credits used"() {
        Message message1 = Mock()
        Message message2 = Mock()
        Message message3 = Mock()

        given:
        protocol.handleOutgoing(new MessageCredits(2))

        when:
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)
        protocol.handleIncoming(message3)

        then:
        1 * context.dispatchIncoming(message1)
        1 * context.dispatchIncoming(message2)
        0 * context._
    }

    def "dispatches outgoing request credits when most incoming messages received and no rooom in queue for extra messages"() {
        Message message1 = Mock()
        Message message2 = Mock()
        Message message3 = Mock()

        when:
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)
        protocol.handleIncoming(message3)

        then:
        0 * context.dispatchOutgoing(_)

        when:
        protocol.handleOutgoing(new MessageCredits(3))

        then:
        1 * context.dispatchOutgoing(new MessageCredits(3))
        0 * context.dispatchOutgoing(_)
    }

    def "dispatches outgoing request credits when most incoming messages received and queue is partially full"() {
        Message message1 = Mock()
        Message message2 = Mock()
        Message message3 = Mock()

        given:
        protocol.handleOutgoing(new MessageCredits(1))
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)

        when:
        protocol.handleIncoming(message3)

        then:
        0 * context._

        when:
        protocol.handleOutgoing(new MessageCredits(2))

        then:
        1 * context.dispatchOutgoing(new MessageCredits(3))
        0 * context.dispatchOutgoing(_)
    }

    def "dispatches outgoing request credits when credits granted"() {
        Message message1 = Mock()
        Message message2 = Mock()

        given:
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)

        when:
        protocol.handleOutgoing(new MessageCredits(8))

        then:
        1 * context.dispatchOutgoing(new MessageCredits(8))
        0 * context.dispatchOutgoing(_)
    }

    def "stop waits until queued messages dispatched"() {
        Message message1 = Mock()
        Message message2 = Mock()

        given:
        protocol.handleIncoming(message1)
        protocol.handleIncoming(message2)

        when:
        protocol.stopRequested()

        then:
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleOutgoing(new MessageCredits(2))

        then:
        1 * context.dispatchIncoming(message1)
        1 * context.dispatchIncoming(message2)
        1 * context.stopped()
        0 * context._
    }

    def "stops immediately when no messages queued"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }
}
