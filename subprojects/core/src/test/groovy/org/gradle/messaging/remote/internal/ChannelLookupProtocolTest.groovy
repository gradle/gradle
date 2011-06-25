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

import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage
import spock.lang.Specification
import org.gradle.messaging.remote.internal.protocol.LookupRequest
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable
import org.gradle.messaging.remote.Address
import java.util.concurrent.TimeUnit
import org.gradle.messaging.remote.internal.protocol.ChannelUnavailable

class ChannelLookupProtocolTest extends Specification {
    final Address address = Mock()
    final ProtocolContext.Callback timeout = Mock()
    final ProtocolContext<DiscoveryMessage> context = Mock()
    final ChannelLookupProtocol protocol = new ChannelLookupProtocol()

    def setup() {
        protocol.start(context)
        _ * context.callbackLater(_, _, _) >> timeout
    }

    def "forwards lookup request"() {
        def request = new LookupRequest("group", "channel")

        when:
        protocol.handleOutgoing(request)

        then:
        1 * context.dispatchOutgoing(request)
    }

    def "forwards channel available response"() {
        def request = new LookupRequest("group", "channel")
        def response = new ChannelAvailable("group", "channel", address)

        given:
        protocol.handleOutgoing(request)

        when:
        protocol.handleIncoming(response)

        then:
        1 * context.dispatchIncoming(response)
        0 * context._
    }

    def "resends lookup request if no response received within timeout"() {
        def request = new LookupRequest("group", "channel")
        def callback

        when:
        protocol.handleOutgoing(request)

        then:
        1 * context.callbackLater(1, TimeUnit.SECONDS, !null) >> { callback = it[2]; return timeout }

        when:
        callback.run()

        then:
        1 * context.dispatchOutgoing(request)
        1 * context.callbackLater(1, TimeUnit.SECONDS, callback)
        0 * context._
    }

    def "cancels timeout when response received"() {
        def request = new LookupRequest("group", "channel")
        def response = new ChannelAvailable("group", "channel", address)

        when:
        protocol.handleOutgoing(request)

        then:
        1 * context.callbackLater(1, TimeUnit.SECONDS, !null) >> { return timeout }

        when:
        protocol.handleIncoming(response)

        then:
        1 * timeout.cancel()
    }

    def "forwards each channel available message received"() {
        final Address address2 = Mock()
        def request = new LookupRequest("group", "channel")
        def response1 = new ChannelAvailable("group", "channel", address)
        def response2 = new ChannelAvailable("group", "channel", address2)

        given:
        protocol.handleOutgoing(request)

        when:
        protocol.handleIncoming(response1)
        protocol.handleIncoming(response2)

        then:
        1 * context.dispatchIncoming(response1)
        1 * context.dispatchIncoming(response2)
        0 * context._
    }

    def "ignores message for unknown channel"() {
        when:
        protocol.handleIncoming(message)

        then:
        0 * context._

        where:
        message << [
                new ChannelAvailable("group", "channel", {} as Address),
                new ChannelUnavailable("group", "channel", {} as Address)
        ]
    }

    def "ignores lookup requests"() {
        when:
        protocol.handleIncoming(new LookupRequest("group", "channel"))

        then:
        0 * context._
    }
}
