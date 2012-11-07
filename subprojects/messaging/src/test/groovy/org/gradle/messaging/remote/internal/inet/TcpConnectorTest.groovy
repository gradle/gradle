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
import org.gradle.internal.os.OperatingSystem
import org.gradle.messaging.remote.ConnectEvent
import org.gradle.messaging.remote.internal.ConnectException
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.DefaultMessageSerializer
import org.gradle.util.ConcurrentSpecification
import org.gradle.internal.id.UUIDGenerator
import spock.lang.IgnoreIf

import java.util.concurrent.CountDownLatch

class TcpConnectorTest extends ConcurrentSpecification {
    final def serializer = new DefaultMessageSerializer<String>(getClass().classLoader)
    final def idGenerator = new UUIDGenerator()
    final def addressFactory = new InetAddressFactory()
    final def outgoingConnector = new TcpOutgoingConnector<String>(serializer)
    final def incomingConnector = new TcpIncomingConnector<String>(executorFactory, serializer, addressFactory, idGenerator)

    def "client can connect to server"() {
        Action action = Mock()

        when:
        def address = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(address)

        then:
        connection != null

        cleanup:
        incomingConnector.requestStop()
    }

    def "client can connect to server using remote addresses"() {
        Action action = Mock()

        when:
        def address = incomingConnector.accept(action, true)
        def connection = outgoingConnector.connect(address)

        then:
        connection != null

        cleanup:
        incomingConnector.requestStop()
    }

    def "server executes action when incoming connection received"() {
        def connectionReceived = startsAsyncAction()
        Action action = Mock()

        when:
        connectionReceived.started {
            def address = incomingConnector.accept(action, false)
            outgoingConnector.connect(address)
        }

        then:
        1 * action.execute(!null) >> { connectionReceived.done() }

        cleanup:
        incomingConnector.requestStop()
    }

    def "client throws exception when cannot connect to server"() {
        def address = new MultiChoiceAddress("address", 12345, [InetAddress.getByName("localhost")])

        when:
        outgoingConnector.connect(address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "the exception includes last failure when cannot connect"() {
        def address = new MultiChoiceAddress("address", 12345, [InetAddress.getByName("localhost"), InetAddress.getByName("127.0.0.1")])

        when:
        outgoingConnector.connect(address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "can receive message from peer after peer has closed connection"() {
        // This is a test to simulate the messaging that the daemon does on build completion, in order to validate some assumptions

        def closed = new CountDownLatch(1)

        when:
        def address = incomingConnector.accept({ ConnectEvent<Connection<Object>> event ->
            def connection = event.connection
            println "[server] connected"
            connection.dispatch("bye")
            connection.stop()
            closed.countDown()
            println "[server] disconnected"
        } as Action, false)

        def connection = outgoingConnector.connect(address)
        println "[client] connected"
        closed.await()
        println "[client] receiving"
        assert connection.receive() == "bye"
        assert connection.receive() == null
        connection.stop()
        println "[client] disconnected"
        incomingConnector.requestStop()

        then:
        finished()

        cleanup:
        incomingConnector.requestStop()
    }

    @IgnoreIf({ OperatingSystem.current().windows })
    def "can receive message from peer when using connection after peer has closed connection"() {
        // This is a test to simulate the messaging that the daemon does on build completion, in order to validate some assumptions

        def closed = new CountDownLatch(1)

        when:
        def address = incomingConnector.accept({ ConnectEvent<Connection<Object>> event ->
            def connection = event.connection
            println "[server] connected"
            connection.dispatch("bye")
            connection.stop()
            closed.countDown()
            println "[server] disconnected"
        } as Action, false)

        def connection = outgoingConnector.connect(address)
        println "[client] connected"
        closed.await()
        println "[client] dispatching"
        connection.dispatch("broken")
        println "[client] receiving"
        assert connection.receive() == "bye"
        assert connection.receive() == null
        connection.stop()
        println "[client] disconnected"
        incomingConnector.requestStop()

        then:
        finished()

        cleanup:
        incomingConnector.requestStop()
    }
}

