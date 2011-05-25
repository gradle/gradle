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
import org.gradle.messaging.remote.internal.protocol.EndOfStreamEvent

class ConnectionDisconnectProtocolTest extends Specification {
    final ProtocolContext<Message> context = Mock()
    final ConnectionDisconnectProtocol protocol = new ConnectionDisconnectProtocol()

    def setup() {
        protocol.start(context)
    }

    def "sends end-of-stream on stop and waits until end-of-stream received"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        1 * context.stopLater()
        0 * context._

        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.stopped()
        0 * context._
    }

    def "forwards incoming messages"() {
        Message message = Mock()

        when:
        protocol.handleIncoming(message)

        then:
        1 * context.dispatchIncoming(message)
        0 * context._
    }

    def "discards incoming messages received during stop"() {
        Message message = Mock()

        given:
        protocol.stopRequested()

        when:
        protocol.handleIncoming(message)

        then:
        0 * context._
    }
}
