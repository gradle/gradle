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

import spock.lang.Specification
import org.gradle.messaging.remote.internal.protocol.ConsumerAvailable
import org.gradle.messaging.remote.internal.protocol.Request
import org.gradle.messaging.remote.internal.protocol.ConsumerUnavailable

class UnicastSendProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final UnicastSendProtocol protocol = new UnicastSendProtocol()

    def setup() {
        protocol.start(context)
    }

    def "queues outgoing messages until a consumer is available"() {
        when:
        protocol.handleOutgoing(new Request("channel", "message1"))
        protocol.handleOutgoing(new Request("channel", "message2"))

        then:
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))

        then:
        1 * context.dispatchOutgoing(new Request("id", "message1"))
        1 * context.dispatchOutgoing(new Request("id", "message2"))
        0 * context._
    }

    def "forwards messages when a consumer is available"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))

        when:
        protocol.handleOutgoing(new Request("channel", "message"))

        then:
        1 * context.dispatchOutgoing(new Request("id", "message"))
    }

    def "stop waits until a consumer is available and messages dispatched"() {
        given:
        protocol.handleOutgoing(new Request("channel", "message"))

        when:
        protocol.stopRequested()

        then:
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channnel"))

        then:
        1 * context.dispatchOutgoing(new Request("id", "message"))
        1 * context.stopped()
        0 * context._
    }

    def "stops immediately when no messages have been dispatched"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "stops immediately when all messages have been dispatched"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))
        protocol.handleOutgoing(new Request("channel", "message"))

        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "discards messages after consumer becomes unavailable"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))
        protocol.handleIncoming(new ConsumerUnavailable("id"))

        when:
        protocol.handleOutgoing(new Request("channel", "message"))

        then:
        0 * context._

        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "stop ignores consumer unavailable when everything dispatched"() {
        given:
        protocol.handleOutgoing(new Request("channel", "message"))
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))

        when:
        protocol.handleIncoming(new ConsumerUnavailable("id"))
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }
}
