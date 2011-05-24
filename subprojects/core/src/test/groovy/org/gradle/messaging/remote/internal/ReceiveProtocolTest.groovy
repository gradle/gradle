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

import org.gradle.messaging.remote.internal.protocol.ConsumerAvailable
import org.gradle.messaging.remote.internal.protocol.ConsumerReady
import org.gradle.messaging.remote.internal.protocol.ConsumerStopping
import org.gradle.messaging.remote.internal.protocol.ProducerReady
import spock.lang.Specification
import org.gradle.messaging.remote.internal.protocol.ProducerStopped
import org.gradle.messaging.remote.internal.protocol.ConsumerUnavailable
import org.gradle.messaging.remote.internal.protocol.ConsumerStopped
import org.gradle.messaging.remote.internal.protocol.Request

class ReceiveProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final ReceiveProtocol protocol = new ReceiveProtocol("id", "display")

    def setup() {
        protocol.start(context)
    }

    def "dispatches incoming consumer available message on start"() {
        when:
        protocol.start(context)

        then:
        1 * context.dispatchIncoming(new ConsumerAvailable("id", "display"))
        0 * context._
    }

    def "acknowledges outgoing producer ready message"() {
        when:
        protocol.handleOutgoing(new ProducerReady("producer", "id", "display"))

        then:
        1 * context.dispatchIncoming(new ConsumerReady("id", "producer"))
        0 * context._
    }

    def "forwards outgoing request to consumer"() {
        Message message = Mock()
        def request = new Request("id", message)

        when:
        protocol.handleOutgoing(request)

        then:
        1 * context.dispatchOutgoing(request)
        0 * context._
    }

    def "dispatches incoming consumer stopping to all producers on stop and waits for acknowledgements"() {
        given:
        protocol.handleOutgoing(new ProducerReady("producer1", "id", "display"))
        protocol.handleOutgoing(new ProducerReady("producer2", "id", "display"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchIncoming(new ConsumerStopping("id", "producer1"))
        1 * context.dispatchIncoming(new ConsumerStopping("id", "producer2"))
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleOutgoing(new ProducerStopped("producer1", "id"))

        then:
        1 * context.dispatchIncoming(new ConsumerStopped("id", "producer1"))
        0 * context._

        when:
        protocol.handleOutgoing(new ProducerStopped("producer2", "id"))

        then:
        1 * context.dispatchIncoming(new ConsumerStopped("id", "producer2"))
        1 * context.dispatchIncoming(new ConsumerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }

    def "acknowledges outgoing producer stopped message"() {
        given:
        protocol.handleOutgoing(new ProducerReady("producer", "id", "display"))

        when:
        protocol.handleOutgoing(new ProducerStopped("producer", "id"))

        then:
        1 * context.dispatchIncoming(new ConsumerStopped("id", "producer"))
        0 * context._
    }

    def "stop does not dispatch consumer stopping to producer which has stopped"() {
        given:
        protocol.handleOutgoing(new ProducerReady("producer", "id", "display"))
        protocol.handleOutgoing(new ProducerStopped("producer", "id"))

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchIncoming(new ConsumerUnavailable("id"))
        1 * context.stopped()
        0 * context._
    }
}
