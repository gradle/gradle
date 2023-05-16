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
package org.gradle.internal.remote.internal.inet

import org.gradle.api.Action
import org.gradle.internal.id.UUIDGenerator
import org.gradle.internal.remote.internal.ConnectCompletion
import org.gradle.internal.remote.internal.ConnectException
import org.gradle.internal.remote.internal.MessageIOException
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.Serializers
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Timeout

import java.nio.channels.SocketChannel

@Timeout(60)
class TcpConnectorTest extends ConcurrentSpec {
    @Shared def serializer = Serializers.stateful(BaseSerializerFactory.STRING_SERIALIZER)
    final def idGenerator = new UUIDGenerator()
    final def addressFactory = new InetAddressFactory()
    final def outgoingConnector = new TcpOutgoingConnector()
    final def incomingConnector = new TcpIncomingConnector(executorFactory, addressFactory, idGenerator)
    @Rule
    public ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    def "client can connect to server"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(serializer)

        then:
        connection != null

        cleanup:
        acceptor?.stop()
        connection?.stop()
    }

    def "client can connect to server using remote addresses"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, true)
        def connection = outgoingConnector.connect(acceptor.address).create(serializer)

        then:
        connection != null

        cleanup:
        acceptor?.stop()
        connection?.stop()
    }

    def "server executes action when incoming connection received"() {
        Action action = Mock()

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(serializer)
        thread.blockUntil.connected

        then:
        1 * action.execute(!null) >> { instant.connected }

        cleanup:
        acceptor?.stop()
        connection?.stop()
    }

    def "client throws exception when cannot connect to server"() {
        def address = new MultiChoiceAddress(idGenerator.generateId(), portAllocator.assignPort(), [InetAddress.getByName("localhost")])

        when:
        outgoingConnector.connect(address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "the exception includes last failure when cannot connect"() {
        def address = new MultiChoiceAddress(idGenerator.generateId(), portAllocator.assignPort(), [InetAddress.getByName("localhost"), InetAddress.getByName("127.0.0.1")])

        when:
        outgoingConnector.connect(address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${address}."
        e.cause instanceof java.net.ConnectException
    }

    def "client cannot connect after server stopped"() {
        when:
        def acceptor = incomingConnector.accept(Mock(Action), false)
        acceptor.stop()
        outgoingConnector.connect(acceptor.address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${acceptor.address}."
        e.cause instanceof java.net.ConnectException
    }

    def "server closes connection when action fails"() {
        Action action = Mock()

        given:
        _ * action.execute(_) >> {
            throw new RuntimeException()
        }

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(serializer)
        def result = connection.receive()

        then:
        result == null

        cleanup:
        connection.stop()
        acceptor?.stop()
    }

    def "server stops accepting connections when action fails"() {
        Action action = Mock()

        given:
        _ * action.execute(_) >> {
            throw new RuntimeException()
        }

        when:
        def acceptor = incomingConnector.accept(action, false)
        async {
            def connection = outgoingConnector.connect(acceptor.address).create(serializer)
            connection.stop()
        }
        outgoingConnector.connect(acceptor.address)

        then:
        ConnectException e = thrown()
        e.message.startsWith "Could not connect to server ${acceptor.address}."
        e.cause instanceof java.net.ConnectException

        cleanup:
        acceptor?.stop()
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
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(serializer)
        thread.blockUntil.connected
        operation.stop {
            acceptor.stop()
        }

        then:
        operation.stop.end > instant.actionFinished

        cleanup:
        acceptor?.stop()
        connection?.stop()
    }

    def "can receive message from peer after peer has closed connection"() {
        // This is a test to simulate the messaging that the daemon does on build completion, in order to validate some assumptions

        when:
        def acceptor = incomingConnector.accept({ ConnectCompletion event ->
            def conn = event.create(serializer)
            conn.dispatch("bye")
            conn.stop()
            instant.closed
        } as Action, false)

        def connection = outgoingConnector.connect(acceptor.address).create(serializer)
        thread.blockUntil.closed

        then:
        connection.receive() == "bye"
        connection.receive() == null

        cleanup:
        connection?.stop()
        acceptor?.stop()
    }

    def "returns null on failure to receive due to truncated input"() {
        given:
        def incomingSerializer = { Encoder encoder, String value ->
            encoder.writeInt(value.length())
        } as Serializer
        def action = { ConnectCompletion completion ->
            def conn = completion.create(Serializers.stateful(incomingSerializer))
            conn.dispatch("string")
            conn.stop()
        } as Action
        def outgoingSerializer = { Decoder decoder ->
            decoder.readInt()
            return decoder.readString()
        } as Serializer

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(Serializers.stateful(outgoingSerializer))
        def result = connection.receive()

        then:
        result == null

        cleanup:
        connection?.stop()
        acceptor?.stop()
    }

    def "reports failure to receive due to broken serializer"() {
        def outgoingSerializer = Mock(Serializer)
        def failure = new RuntimeException()

        given:
        def action = { ConnectCompletion completion ->
            def conn = completion.create(serializer)
            conn.dispatch("string")
            conn.stop()
        } as Action
        outgoingSerializer.read(_) >> { Decoder decoder ->
            throw failure
        }

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(Serializers.stateful(outgoingSerializer))
        connection.receive()

        then:
        MessageIOException e = thrown()
        e.message == "Could not read message from '${connection.remoteAddress}'."
        e.cause == failure

        cleanup:
        connection?.stop()
        acceptor?.stop()
    }

    @Issue("GRADLE-2316")
    @Requires(UnitTestPreconditions.NotMacOs) // https://github.com/gradle/gradle-private/issues/2832
    def "detects self connect when outgoing connection binds to same port"() {
        given:
        def socketChannel = SocketChannel.open()
        def communicationAddress = addressFactory.getLocalBindingAddress()
        def bindAnyPort = new InetSocketAddress(communicationAddress, 0)
        socketChannel.socket().bind(bindAnyPort)
        def selfConnect = new InetSocketAddress(communicationAddress, socketChannel.socket().getLocalPort())

        when:
        socketChannel.socket().connect(selfConnect)

        then:
        outgoingConnector.detectSelfConnect(socketChannel)

        cleanup:
        socketChannel.close()
    }

    @Issue("GRADLE-2316")
    def "does not detect self connect when outgoing connection bind to different ports"() {
        given:
        def action = Mock(Action)
        def socketChannel = SocketChannel.open()
        def acceptor = incomingConnector.accept(action, false)
        def communicationAddress = addressFactory.getLocalBindingAddress()
        def bindAnyPort = new InetSocketAddress(communicationAddress, 0)
        def connectAddress = new InetSocketAddress(communicationAddress, acceptor.address.port)

        when:
        socketChannel.socket().bind(bindAnyPort)
        socketChannel.socket().connect(connectAddress)

        then:
        !outgoingConnector.detectSelfConnect(socketChannel)

        cleanup:
        acceptor?.stop()
        socketChannel.close()
    }
}

