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

import org.gradle.messaging.remote.internal.protocol.EndOfStreamEvent
import spock.lang.Specification

class RemoteDisconnectProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final RemoteDisconnectProtocol protocol = new RemoteDisconnectProtocol()

    def setup() {
        protocol.start(context)
    }

    def "stop dispatches outgoing end-of-stream request and waits for end-of-stream"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._

        when:
        protocol.handleOutgoing(new EndOfStreamEvent())

        then:
        1 * context.stopped()
        0 * context._
    }

    def "dispatches incoming end-of-stream when incoming disconnect request received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._

        when:
        protocol.handleOutgoing(new EndOfStreamEvent())

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        0 * context._
    }

    def "stop waits until outgoing end-of-stream received when incoming previously end-of-stream received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleOutgoing(new EndOfStreamEvent())

        then:
        1 * context.stopped()
        0 * context._
    }

    def "stop does not wait when outgoing end-of-stream has already been received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchIncoming(new EndOfStreamEvent())
        0 * context._

        when:
        protocol.handleOutgoing(new EndOfStreamEvent())

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        0 * context._

        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "discards outgoing messages after outgoing end-of-stream dispatched"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleOutgoing(new Message() {})

        then:
        0 * context._
    }
}
