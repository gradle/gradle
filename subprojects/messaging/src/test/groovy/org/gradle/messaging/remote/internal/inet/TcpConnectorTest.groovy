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
package org.gradle.messaging.remote.internal.inet

import org.gradle.api.Action
import org.gradle.internal.id.UUIDGenerator
import org.gradle.messaging.remote.ConnectEvent
import org.gradle.messaging.remote.internal.ConnectException
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.DefaultMessageSerializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class TcpConnectorTest extends ConcurrentSpec {
    final def serializer = new DefaultMessageSerializer<String>(getClass().classLoader)
    final def idGenerator = new UUIDGenerator()
    final def addressFactory = new InetAddressFactory()
    final def outgoingConnector = new TcpOutgoingConnector()
    final def incomingConnector = new TcpIncomingConnector(executorFactory, addressFactory, idGenerator)

    def "client can connect to server"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, serializer, false)
        def connection = outgoingConnector.connect(acceptor.address, serializer)

        then:
        connection != null

        cleanup:
        acceptor?.requestStop()
    }

    def "client can connect to server using remote addresses"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, serializer, true)
        def connection = outgoingConnector.connect(acceptor.address, serializer)

        then:
        connection != null

        cleanup:
        acceptor?.requestStop()
    }

    def "server executes action when incoming connection received"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, serializer, false)
        outgoingConnector.connect(acceptor.address, serializer)
        thread.blockUntil.connected

        then:
        1 * action.execute(!null) >> { instant.connected }

        cleanup:
        acceptor?.requestStop()
    }

    def "client throws exception when cannot connect to server"() {
        def address = new MultiChoiceAddress("address", 12345, [InetAddress.getByName("localhost")])

        when:
        outgoingConnector.connect(address, serializer)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "the exception includes last failure when cannot connect"() {
        def address = new MultiChoiceAddress("address", 12345, [InetAddress.getByName("localhost"), InetAddress.getByName("127.0.0.1")])

        when:
        outgoingConnector.connect(address, serializer)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "client cannot connect when server has requested stop"() {
        when:
        def acceptor = incomingConnector.accept(Mock(Action), serializer, false)
        acceptor.requestStop()
        outgoingConnector.connect(acceptor.address, serializer)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${acceptor.address}."
        e.cause instanceof java.net.ConnectException
    }

    def "server stops accepting connections when action fails"() {
        Action action = Mock()

        given:
        _ * action.execute(_) >> {
            throw new RuntimeException()
        }

        when:
        def acceptor = incomingConnector.accept(action, serializer, false)
        async {
            outgoingConnector.connect(acceptor.address, serializer)
        }
        outgoingConnector.connect(acceptor.address, serializer)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${acceptor.address}."
        e.cause instanceof java.net.ConnectException

        cleanup:
        acceptor?.requestStop()
    }

    def "acceptor stop blocks until accept action has completed"() {
        Action action = Mock()

        given:
        1 * action.execute(_) >> {
            instant.connected
            thread.block()
            instant.actionFinished
        }

        when:
        def acceptor = incomingConnector.accept(action, serializer, false)
        outgoingConnector.connect(acceptor.address, serializer)
        thread.blockUntil.connected
        operation.stop {
            acceptor.stop()
        }

        then:
        operation.stop.end > instant.actionFinished

        cleanup:
        acceptor?.requestStop()
    }

    def "can receive message from peer after peer has closed connection"() {
        // This is a test to simulate the messaging that the daemon does on build completion, in order to validate some assumptions

        when:
        def acceptor = incomingConnector.accept({ ConnectEvent<Connection<Object>> event ->
            def connection = event.connection
            connection.dispatch("bye")
            connection.stop()
            instant.closed
        } as Action, serializer, false)

        def connection = outgoingConnector.connect(acceptor.address, serializer)
        thread.blockUntil.closed

        then:
        connection.receive() == "bye"
        connection.receive() == null

        cleanup:
        connection?.stop()
        acceptor?.stop()
    }
}

