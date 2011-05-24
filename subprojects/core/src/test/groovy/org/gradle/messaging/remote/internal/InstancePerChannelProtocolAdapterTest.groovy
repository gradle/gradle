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
import org.gradle.messaging.remote.internal.protocol.ChannelMessage

class InstancePerChannelProtocolAdapterTest extends Specification {
    final ProtocolContext<ChannelMessage> context = Mock()
    final InstancePerChannelProtocolAdapter.ChannelProtocolFactory factory = Mock()
    final Protocol<String> channel1 = Mock()
    ProtocolContext<String> context1 = Mock()
    final Protocol<String> channel2 = Mock()
    ProtocolContext<String> context2 = Mock()
    final InstancePerChannelProtocolAdapter adapter = new InstancePerChannelProtocolAdapter(String.class, factory)

    def setup() {
        _ * channel1.start(!null) >> { context1 = it[0] }
        _ * channel2.start(!null) >> { context2 = it[0] }
        _ * factory.newChannel("channel") >> channel1
        _ * factory.newChannel("channel2") >> channel2

        adapter.start(context)
    }

    def "creates and starts channel specific protocol instance when incoming channel message dispatched"() {
        when:
        adapter.handleIncoming(message("channel", "message"))

        then:
        1 * factory.newChannel("channel") >> channel1
        1 * channel1.start(!null)
    }

    def "creates and starts channel specific protocol instance when outgoing channel message dispatched"() {
        when:
        adapter.handleOutgoing(message("channel", "message"))

        then:
        1 * factory.newChannel("channel") >> channel1
        1 * channel1.start(!null)
    }

    def "routes incoming channel message to channel specific instance"() {
        when:
        adapter.handleIncoming(message("channel", "message1"))
        adapter.handleIncoming(message("channel2", "message2"))

        then:
        1 * channel1.handleIncoming("message1")
        1 * channel2.handleIncoming("message2")
    }

    def "routes outgoing channel message to channel specific instance"() {
        when:
        adapter.handleOutgoing(message("channel", "message1"))
        adapter.handleOutgoing(message("channel2", "message2"))

        then:
        1 * channel1.handleOutgoing("message1")
        1 * channel2.handleOutgoing("message2")
    }

    def "wraps incoming messages dispatched by channel specific instance"() {
        given:
        channelsStarted()

        when:
        context1.dispatchIncoming("message")

        then:
        1 * context.dispatchIncoming(new ChannelMessage("channel", "message"))
    }

    def "wraps outgoing messages dispatched by channel specific instance"() {
        given:
        channelsStarted()

        when:
        context1.dispatchOutgoing("message")

        then:
        1 * context.dispatchOutgoing(new ChannelMessage("channel", "message"))
    }

    def "stops all protocol instances when stop requested"() {
        given:
        channelsStarted()

        when:
        adapter.stopRequested()

        then:
        1 * channel1.stopRequested()
        1 * channel2.stopRequested()
        1 * context.stopped()
        0 * context._
    }

    def "protocol instance can defer stop"() {
        given:
        channelsStarted()

        when:
        adapter.stopRequested()

        then:
        1 * channel1.stopRequested() >> {context1.stopLater()}
        1 * channel2.stopRequested()
        1 * context.stopLater()

        when:
        context1.stopped()

        then:
        1 * context.stopped()
        0 * context._
    }

    def "stops a channel when channel created while stopping"() {
        given:
        adapter.handleOutgoing(message("channel", "message"))

        when:
        adapter.stopRequested()

        then:
        1 * channel1.stopRequested() >> {context1.stopLater()}
        1 * context.stopLater()
        0 * context._

        when:
        adapter.handleOutgoing(message("channel2", "message"))

        then:
        1 * channel2.stopRequested() >> {context2.stopLater()}
        0 * context._

        when:
        context1.stopped()
        context2.stopped()

        then:
        1 * context.stopped()
        0 * context._
    }

    def channelsStarted() {
        adapter.handleOutgoing(message("channel", "message"))
        adapter.handleOutgoing(message("channel2", "message"))
    }

    def message(String channel, def payload) {
        return new ChannelMessage(channel, payload)
    }
}
