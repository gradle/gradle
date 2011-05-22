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

class EndOfStreamHandshakeProtocolTest extends Specification {
    final ProtocolContext<Object> context = Mock()
    final Runnable endOfStreamAction = Mock()
    final EndOfStreamHandshakeProtocol protocol = new EndOfStreamHandshakeProtocol(endOfStreamAction)

    def setup() {
        protocol.start(context)
    }

    def "dispatches outgoing EOS message when stop requested"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.stopLater()
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        0 * _._
    }

    def "sends return EOS message when EOS message received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())
        0 * context._
    }

    def "notifies context when stop requested after EOS message received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.dispatchOutgoing(new EndOfStreamEvent())

        when:
        protocol.stopRequested()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "notifies context when EOS message received after stop requested"() {
        when:
        protocol.stopRequested()

        then:
        1 * context.stopLater()
        1 * context.dispatchOutgoing(new EndOfStreamEvent())

        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * context.stopped()
        0 * context._
    }
    def "runs action when EOS message received"() {
        when:
        protocol.handleIncoming(new EndOfStreamEvent())

        then:
        1 * endOfStreamAction.run()
    }
}
