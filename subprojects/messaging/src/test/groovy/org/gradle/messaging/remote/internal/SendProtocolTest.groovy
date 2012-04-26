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
import org.gradle.messaging.remote.internal.protocol.*

class SendProtocolTest extends Specification {
    final ProtocolContext<Object> context = Mock()
    final SendProtocol protocol = new SendProtocol("id", "display", "channel")

    def setup() {
        protocol.start(context)
    }

    def "dispatches outgoing producer available message on start"() {
        when:
        protocol.start(context)

        then:
        1 * context.dispatchOutgoing(new ProducerAvailable("id", "display", "channel"))
        0 * context._
    }

    def "dispatches outgoing producer ready when incoming consumer available received"() {
        when:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "consumer-display", "channel"))

        then:
        1 * context.dispatchOutgoing(new ProducerReady("id", "consumer"))
        0 * context._
    }

    def "dispatches incoming consumer available when consumer ready received"() {
        def available = new ConsumerAvailable("consumer", "display", "channel")

        given:
        protocol.handleIncoming(available)

        when:
        protocol.handleIncoming(new ConsumerReady("consumer", "id"))

        then:
        1 * context.dispatchIncoming(available)
        0 * context._
    }

    def "dispatches incoming consumer unavailable and outgoing producer stopped when consumer stopping received"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "display", "channel"))

        when:
        protocol.handleIncoming(new ConsumerStopping("consumer", "id"))

        then:
        1 * context.dispatchIncoming(new ConsumerUnavailable("consumer"))
        1 * context.dispatchOutgoing(new ProducerStopped("id", "consumer"))
        0 * context._
    }

    def "stop dispatches outgoing producer stopped to all consumers and waits for acknowledgement"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer1", "display", "channel"))
        protocol.handleIncoming(new ConsumerReady("consumer1", "id"))
        protocol.handleIncoming(new ConsumerAvailable("consumer2", "display", "channel"))
        protocol.handleIncoming(new ConsumerReady("consumer2", "id"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerStopped("id", "consumer1"))
        1 * context.dispatchOutgoing(new ProducerStopped("id", "consumer2"))
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerStopped("consumer1", "id"))

        then:
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerStopped("consumer2", "id"))

        then:
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "stops when no consumers"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "does not dispatch stopped message to consumer which has stopped"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "display", "channel"))
        protocol.handleIncoming(new ConsumerStopping("consumer", "id"))
        protocol.handleIncoming(new ConsumerStopped("consumer", "id"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "handles consumer which becomes unavailable while waiting for consumer ready"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "display", "channel"))

        when:
        protocol.handleIncoming(new ConsumerUnavailable("consumer"))

        then:
        0 * context._

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "handles consumer which becomes unavailable while waiting for consumer stopped"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "display", "channel"))
        protocol.handleIncoming(new ConsumerReady("consumer", "id"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerStopped("id", "consumer"))
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleIncoming(new ConsumerUnavailable("consumer"))

        then:
        1 * context.dispatchIncoming(new ConsumerUnavailable("consumer"))
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "handles consumer which becomes unavailable without consumer stopping message received"() {
        given:
        protocol.handleIncoming(new ConsumerAvailable("consumer", "display", "channel"))
        protocol.handleIncoming(new ConsumerReady("consumer", "id"))

        when:
        protocol.handleIncoming(new ConsumerUnavailable("consumer"))

        then:
        1 * context.dispatchIncoming(new ConsumerUnavailable("consumer"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new ProducerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }
}
