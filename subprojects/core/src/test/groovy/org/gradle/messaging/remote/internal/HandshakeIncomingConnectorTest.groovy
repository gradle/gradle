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
import java.util.concurrent.Executor
import org.gradle.messaging.remote.ConnectEvent
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.protocol.ConnectRequest

class HandshakeIncomingConnectorTest extends Specification {
    private final Address localAddress = Mock()
    private final Executor executor = Mock()
    private final IncomingConnector target = Mock()
    private final Connection connection = Mock()
    private final HandshakeIncomingConnector connector = new HandshakeIncomingConnector(target, executor)

    def acceptAllocatesAUriAndStartsListening() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()

        when:
        def address = connector.accept(action, false)

        then:
        address == new CompositeAddress(localAddress, 0L)
        1 * target.accept(!null, false) >> localAddress
        0 * target._
    }

    def eachCallToAcceptAllocatesADifferentAddress() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()

        when:
        def address1 = connector.accept(action, false)
        def address2 = connector.accept(action, false)

        then:
        address1 == new CompositeAddress(localAddress, 0L)
        address2 == new CompositeAddress(localAddress, 1L)
        1 * target.accept(!null, false) >> localAddress
        0 * target._
    }
    
    def performsHandshakeOnAccept() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()
        Address remoteAddress = Mock()
        def wrappedAction
        def handshakeRunnable
        1 * target.accept(!null, false) >> { wrappedAction = it[0]; localAddress }

        def address = connector.accept(action, false)

        when:
        wrappedAction.execute(new ConnectEvent<Connection<Message>>(connection, localAddress, remoteAddress))

        then:
        1 * executor.execute(!null) >> { handshakeRunnable = it[0] }

        when:
        handshakeRunnable.run()

        then:
        1 * connection.receive() >> new ConnectRequest(address)
        1 * action.execute({ it instanceof ConnectEvent && it.connection == connection && it.localAddress == address})
    }
}
