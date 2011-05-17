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

class ChannelMultiplexProtocolTest extends Specification {
    final ProtocolContext<Object> context = Mock()
    final ChannelMultiplexProtocol protocol = new ChannelMultiplexProtocol()

    def setup() {
        protocol.start(context)
    }

    def "maps channel key to channel id for outgoing message"() {
        when:
        protocol.handleOutgoing(new ChannelMessage("channel", "message1"));
        protocol.handleOutgoing(new ChannelMessage("channel", "message2"));

        then:
        1 * context.dispatchOutgoing(new ChannelMetaInfo("channel", 0));
        1 * context.dispatchOutgoing(new ChannelMessage(0, "message1"));
        1 * context.dispatchOutgoing(new ChannelMessage(0, "message2"));
    }

    def "forwards unknown outgoing message"() {
        when:
        protocol.handleOutgoing("message")

        then:
        1 * context.dispatchOutgoing("message")
    }

    def "maps channel id to channel key for incoming message"() {
        when:
        protocol.handleIncoming(new ChannelMetaInfo("channel", 0));
        protocol.handleIncoming(new ChannelMessage(0, "message1"));
        protocol.handleIncoming(new ChannelMessage(0, "message2"));

        then:
        1 * context.dispatchIncoming(new ChannelMessage("channel", "message1"));
        1 * context.dispatchIncoming(new ChannelMessage("channel", "message2"));
    }

    def "forwards unknown incoming message"() {
        when:
        protocol.handleIncoming("message")

        then:
        1 * context.dispatchIncoming("message")
    }
}
