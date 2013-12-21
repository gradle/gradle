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

import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.protocol.ChannelAvailable
import org.gradle.messaging.remote.internal.protocol.ChannelUnavailable
import org.gradle.messaging.remote.internal.protocol.DiscoveryMessage
import org.gradle.messaging.remote.internal.protocol.LookupRequest
import spock.lang.Specification

class ChannelRegistrationProtocolTest extends Specification {
    final Address address = Mock()
    final ProtocolContext<DiscoveryMessage> context = Mock()
    final MessageOriginator messageSource = Mock()
    final protocol = new ChannelRegistrationProtocol(messageSource)

    def setup() {
        protocol.start(context)
    }

    def "forwards channel available message when channel registered"() {
        def message = new ChannelAvailable(messageSource, "group", "channel", address)

        when:
        protocol.handleOutgoing(message)

        then:
        1 * context.dispatchOutgoing(message)
        0 * context._
    }

    def "sends channel unavailable message for all available channels when registry stopped"() {
        def availableMessage = new ChannelAvailable(messageSource, "group", "channel", address)
        def unavailableMessage = new ChannelUnavailable(messageSource, "group", "channel", address)

        protocol.handleOutgoing(availableMessage)

        when:
        protocol.stopRequested()

        then:
        1 * context.dispatchOutgoing(unavailableMessage)
        1 * context.stopped()
        0 * context._
    }

    def "sends channel available message when lookup request received"() {
        def lookupRequest = new LookupRequest(messageSource, "group", "channel")
        def availableMessage = new ChannelAvailable(messageSource, "group", "channel", address)

        protocol.handleOutgoing(availableMessage)

        when:
        protocol.handleIncoming(lookupRequest)

        then:
        1 * context.dispatchOutgoing(availableMessage)
        0 * context._
    }

    def "ignores incoming broadcast messages"() {
        when:
        MessageOriginator messageSource = Mock()
        protocol.handleIncoming(message)

        then:
        0 * context._

        where:
        message << [
            new ChannelAvailable(messageSource, "group", "channel", null),
            new ChannelUnavailable(messageSource, "group", "channel", null)
        ]
    }

    def "ignores lookup request for unknown channel"() {
        when:
        protocol.handleIncoming(new LookupRequest(messageSource, "group", "other"))

        then:
        0 * context._
    }
}
