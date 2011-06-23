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

class ReceiveProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final ReceiveProtocol protocol = new ReceiveProtocol("id", "display", "channel")

    def setup() {
        protocol.start(context)
    }

    def "dispatches incoming consumer available message on start"() {
        when:
        protocol.start(context)

        then:
        1 * context.dispatchOutgoing(new ConsumerAvailable("id", "display", "channel"))
        0 * context._
    }

    def "acknowledges outgoing producer ready message"() {
        when:
        protocol.handleIncoming(new ProducerReady("producer", "id"))

        then:
        1 * context.dispatchOutgoing(new ConsumerReady("id", "producer"))
        0 * context._
    }

    def "forwards outgoing request to consumer"() {
        Message message = Mock()
        def request = new Request("id", message)

        when:
        protocol.handleIncoming(request)

        then:
        1 * context.dispatchIncoming(request)
        0 * context._
    }

    def "dispatches incoming consumer stopping to all producers on worker stop and waits for acknowledgements"() {
        given:
        protocol.handleIncoming(new ProducerReady("producer1", "id"))
        protocol.handleIncoming(new ProducerReady("producer2", "id"))

        when:
        protocol.handleOutgoing(new WorkerStopping())

        then:
        1 * context.dispatchOutgoing(new ConsumerStopping("id", "producer1"))
        1 * context.dispatchOutgoing(new ConsumerStopping("id", "producer2"))
        0 * context._

        when:
        protocol.handleIncoming(new ProducerStopped("producer1", "id"))

        then:
        1 * context.dispatchOutgoing(new ConsumerStopped("id", "producer1"))
        0 * context._

        when:
        protocol.handleIncoming(new ProducerStopped("producer2", "id"))

        then:
        1 * context.dispatchOutgoing(new ConsumerStopped("id", "producer2"))
        1 * context.dispatchOutgoing(new ConsumerUnavailable("id"))
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._
    }

    def "acknowledges outgoing producer stopped message"() {
        given:
        protocol.handleIncoming(new ProducerReady("producer", "id"))

        when:
        protocol.handleIncoming(new ProducerStopped("producer", "id"))

        then:
        1 * context.dispatchOutgoing(new ConsumerStopped("id", "producer"))
        0 * context._
    }

    def "worker stop does not dispatch consumer stopping to producer which has stopped"() {
        given:
        protocol.handleIncoming(new ProducerReady("producer", "id"))
        protocol.handleIncoming(new ProducerStopped("producer", "id"))

        when:
        protocol.handleOutgoing(new WorkerStopping())

        then:
        1 * context.dispatchOutgoing(new ConsumerUnavailable("id"))
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._
    }

    def "worker stop does not dispatch consumer stopping to producer which becomes unavailable"() {
        given:
        protocol.handleIncoming(new ProducerReady("producer", "id"))
        protocol.handleIncoming(new ProducerUnavailable("producer"))

        when:
        protocol.handleOutgoing(new WorkerStopping())

        then:
        1 * context.dispatchOutgoing(new ConsumerUnavailable("id"))
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._
    }

    def "worker stop does not wait for producer which becomes unavailable during stop"() {
        given:
        protocol.handleIncoming(new ProducerReady("producer", "id"))
        protocol.handleOutgoing(new WorkerStopping())

        when:
        protocol.handleIncoming(new ProducerUnavailable("producer"))

        then:
        1 * context.dispatchOutgoing(new ConsumerUnavailable("id"))
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._
    }
}
