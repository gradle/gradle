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
import org.gradle.messaging.remote.internal.protocol.Request
import org.gradle.messaging.remote.internal.protocol.ConsumerAvailable
import org.gradle.messaging.remote.internal.protocol.ConsumerUnavailable
import java.util.concurrent.TimeUnit

class BroadcastSendProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final BroadcastSendProtocol protocol = new BroadcastSendProtocol()

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

    def "dispatches outgoing message to each consumer"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("id1", "display", "channel"))
        protocol.handleIncoming(new ConsumerAvailable("id2", "display", "channel"))

        when:
        protocol.handleOutgoing(new Request("channel", "message"))

        then:
        1 * context.dispatchOutgoing(new Request("id1", "message"))
        1 * context.dispatchOutgoing(new Request("id2", "message"))
        0 * context._
    }

    def "stops dispatching to a consumer when it becomes unavailable"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))

        when:
        protocol.handleIncoming(new ConsumerUnavailable("id"))
        protocol.handleOutgoing(new Request("channel", "message"))

        then:
        0 * context._
    }

    def "stop waits until for a consumer to become available and queued messages dispatched"() {
        given:
        protocol.handleOutgoing(new Request("channel", "message"))

        when:
        protocol.stopRequested()

        then:
        1 * context.stopLater()
        1 * context.callbackLater(5, TimeUnit.SECONDS, !null)
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerAvailable("id", "display", "channel"))

        then:
        1 * context.dispatchOutgoing(new Request("id", "message"))
        1 * context.stopped()
        0 * context._
    }

    def "stop waits until timeout for a consumer to become available and queued messages dispatched"() {
        Runnable callback

        when:
        protocol.handleOutgoing(new Request("channel", "message"))
        protocol.stopRequested()

        then:
        1 * context.callbackLater(5, TimeUnit.SECONDS, !null) >> { callback = it[2]; return null }

        when:
        callback.run()

        then:
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
}
