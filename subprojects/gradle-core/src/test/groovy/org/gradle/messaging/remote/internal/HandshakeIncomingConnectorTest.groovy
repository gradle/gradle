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

class HandshakeIncomingConnectorTest extends Specification {
    private final Executor executor = Mock()
    private final IncomingConnector target = Mock()
    private final Connection connection = Mock()
    private final HandshakeIncomingConnector connector = new HandshakeIncomingConnector(target, executor)

    def acceptAllocatesAUriAndStartsListening() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()

        when:
        def address = connector.accept(action)

        then:
        1 * target.accept(!null) >> new URI("test:source")
        address == new URI("channel:test:source!0")
    }

    def eachCallToAcceptAllocatesADifferentUri() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()
        1 * target.accept(!null) >> new URI("test:source")

        when:
        def address1 = connector.accept(action)
        def address2 = connector.accept(action)

        then:
        address1 == new URI("channel:test:source!0")
        address2 == new URI("channel:test:source!1")
    }
    
    def performsHandshakeOnAccept() {
        Action<ConnectEvent<Connection<Message>>> action = Mock()
        def wrappedAction
        def handshakeRunnable
        1 * target.accept(!null) >> { wrappedAction = it[0]; new URI("test:source") }

        def address = connector.accept(action)

        when:
        wrappedAction.execute(new ConnectEvent<Connection<Message>>(connection, new URI("test:source"), new URI("test:dest")))

        then:
        1 * executor.execute(!null) >> { handshakeRunnable = it[0] }

        when:
        handshakeRunnable.run()

        then:
        1 * connection.receive() >> new ConnectRequest(address)
        1 * action.execute({ it instanceof ConnectEvent && it.connection == connection && it.localAddress == address})
    }
}
