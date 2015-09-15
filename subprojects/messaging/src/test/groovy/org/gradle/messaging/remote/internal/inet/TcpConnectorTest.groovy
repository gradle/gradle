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
import org.gradle.internal.serialize.*
import org.gradle.messaging.remote.internal.*
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.ports.ReleasingPortAllocator
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

import java.nio.channels.SocketChannel

@Timeout(60)
class TcpConnectorTest extends ConcurrentSpec {
    @Shared def serializer = new DefaultMessageSerializer<String>(getClass().classLoader)
    @Shared def kryoSerializer = new KryoBackedMessageSerializer<String>(Serializers.stateful(BaseSerializerFactory.STRING_SERIALIZER))
    final def idGenerator = new UUIDGenerator()
    final def addressFactory = new InetAddressFactory()
    final def outgoingConnector = new TcpOutgoingConnector()
    final def incomingConnector = new TcpIncomingConnector(executorFactory, addressFactory, idGenerator)
    @Rule ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

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

    @Unroll
    def "can receive message from peer after peer has closed connection using #serializerName"() {
        // This is a test to simulate the messaging that the daemon does on build completion, in order to validate some assumptions

        when:
        def acceptor = incomingConnector.accept({ ConnectCompletion event ->
            def connection = event.create(messageSerializer)
            connection.dispatch("bye")
            connection.stop()
            instant.closed
        } as Action, false)

        def connection = outgoingConnector.connect(acceptor.address).create(messageSerializer)
        thread.blockUntil.closed

        then:
        connection.receive() == "bye"
        connection.receive() == null

        cleanup:
        connection?.stop()
        acceptor?.stop()

        where:
        messageSerializer | serializerName
        serializer        | "java"
        kryoSerializer    | "kryo"
    }

    def "returns null on failure to receive due to truncated input"() {
        given:
        def incomingSerializer = { Encoder encoder, String value ->
            encoder.writeInt(value.length())
        } as Serializer
        def action = { ConnectCompletion completion ->
            def connection = completion.create(new KryoBackedMessageSerializer<String>(Serializers.stateful(incomingSerializer)))
            connection.dispatch("string")
            connection.stop()
        } as Action
        def outgoingSerializer = { Decoder decoder ->
            decoder.readInt()
            return decoder.readString()
        } as Serializer

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(new KryoBackedMessageSerializer<String>(Serializers.stateful(outgoingSerializer)))
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
            def connection = completion.create(kryoSerializer)
            connection.dispatch("string")
            connection.stop()
        } as Action
        outgoingSerializer.read(_) >> { Decoder decoder ->
            throw failure
        }

        when:
        def acceptor = incomingConnector.accept(action, false)
        def connection = outgoingConnector.connect(acceptor.address).create(new KryoBackedMessageSerializer<String>(Serializers.stateful(outgoingSerializer)))
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
    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "detects self connect when outgoing connection binds to same port"() {
        given:
        def socketChannel = SocketChannel.open()
        def port = portAllocator.assignPort()
        def localAddress = addressFactory.findLocalAddresses().find { it instanceof Inet6Address }
        def selfConnect = new InetSocketAddress(localAddress, port)

        when:
        socketChannel.socket().bind(selfConnect)
        socketChannel.socket().connect(selfConnect)
        then:
        outgoingConnector.detectSelfConnect(socketChannel)

        cleanup:
        socketChannel.close()
    }

    @Issue("GRADLE-2316")
    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "does not detect self connect when outgoing connection bind to different ports"() {
        given:
        def action = Mock(Action)
        def socketChannel = SocketChannel.open()
        def acceptor = incomingConnector.accept(action, false)
        def localAddress = addressFactory.findLocalAddresses().find { it instanceof Inet6Address }
        def bindPort = portAllocator.assignPort()
        def bindAddress = new InetSocketAddress(localAddress, bindPort)
        def connectAddress = new InetSocketAddress(localAddress, acceptor.address.port)

        when:
        socketChannel.socket().bind(bindAddress)
        socketChannel.socket().connect(connectAddress)

        then:
        !outgoingConnector.detectSelfConnect(socketChannel)

        cleanup:
        acceptor?.stop()
        socketChannel.close()
    }
}

