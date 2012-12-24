/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.Action
import org.gradle.messaging.remote.ConnectEvent
import org.gradle.messaging.remote.ObjectConnection
import org.gradle.messaging.remote.Address

class DefaultMessagingServerTest extends Specification {
    final Address remoteAddress = Mock()
    final Address localAddress = Mock()
    private final MultiChannelConnector multiChannelConnector = Mock()
    private final DefaultMessagingServer server = new DefaultMessagingServer(multiChannelConnector)

    def createsConnection() {
        Action<ConnectEvent<ObjectConnection>> action = Mock()
        Action<ConnectEvent<MultiChannelConnection<Message>>> wrappedAction = Mock()
        MultiChannelConnection<Message> connection = Mock()

        when:
        def address = server.accept(action)

        then:
        address == localAddress
        1 * multiChannelConnector.accept(!null) >> { wrappedAction = it[0]; return localAddress }

        when:
        wrappedAction.execute(new ConnectEvent(connection, localAddress, remoteAddress))

        then:
        1 * action.execute(!null) >> {
            ConnectEvent event = it[0]
            assert event.connection instanceof DefaultObjectConnection
            assert event.localAddress == localAddress
            assert event.remoteAddress == remoteAddress
        }
    }

    def stopsConnectionsOnServerStop() {
        MultiChannelConnection<Message> channelConnection = Mock()
        expectConnectionCreated(channelConnection)

        when:
        server.stop()

        then:
        1 * channelConnection.stop()
    }

    def discardsConnectionOnStop() {
        MultiChannelConnection<Message> channelConnection = Mock()
        ObjectConnection connection = expectConnectionCreated(channelConnection)

        when:
        connection.stop()

        then:
        1 * channelConnection.stop()

        when:
        server.stop()

        then:
        0 * channelConnection._
    }

    private ObjectConnection expectConnectionCreated(MultiChannelConnection<Message> channelConnection) {
        Action<ConnectEvent<ObjectConnection>> action = Mock()
        ObjectConnection connection

        1 * multiChannelConnector.accept(!null) >> {
            def wrappedAction = it[0]
            wrappedAction.execute(new ConnectEvent(channelConnection, localAddress, remoteAddress))
        }
        1 * action.execute(!null) >> {
            connection = it[0].connection
        }
        server.accept(action)
        return connection
    }
}
