/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub

import org.gradle.api.Action
import org.gradle.internal.id.UUIDGenerator
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import org.gradle.messaging.remote.internal.inet.InetAddressFactory
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch

@Timeout(60)
class MessageHubIntegrationTest extends ConcurrentSpec {
    final TcpOutgoingConnector<InterHubMessage> outgoingConnector = new TcpOutgoingConnector<InterHubMessage>(new InterHubMessageSerializer())
    final TcpIncomingConnector<InterHubMessage> incomingConnector = new TcpIncomingConnector<InterHubMessage>(executorFactory, new InterHubMessageSerializer(), new InetAddressFactory(), new UUIDGenerator())
    final Action<Throwable> errorHandler = Mock()

    def cleanup() {
        incomingConnector?.stop()
    }

    def "can wire two hubs together"() {
        Dispatch<String> clientHandler = Mock()
        Dispatch<String> serverHandler = Mock()
        def server = server()
        def client = client(server)
        server.hub.addHandler("channel", serverHandler)
        client.hub.addHandler("channel", clientHandler)

        def serverDispatch = server.hub.getOutgoing("channel", String)
        def clientDispatch = client.hub.getOutgoing("channel", String)

        when:
        serverDispatch.dispatch("message 1")
        serverDispatch.dispatch("message 2")
        serverDispatch.dispatch("message 3")
        serverDispatch.dispatch("message 4")
        serverDispatch.dispatch("message 5")
        serverDispatch.dispatch("message 6")
        serverDispatch.dispatch("message 7")
        thread.blockUntil.repliesReceived
        server.stop()
        client.stop()

        then:
        1 * clientHandler.dispatch("message 1") >> { clientDispatch.dispatch("[message 1]") }
        1 * clientHandler.dispatch("message 2") >> { clientDispatch.dispatch("[message 2]") }
        1 * clientHandler.dispatch("message 3")
        1 * clientHandler.dispatch("message 4")
        1 * clientHandler.dispatch("message 5")
        1 * clientHandler.dispatch("message 6")
        1 * clientHandler.dispatch("message 7") >> { clientDispatch.dispatch("[message 3]"); }
        1 * serverHandler.dispatch("[message 1]")
        1 * serverHandler.dispatch("[message 2]")
        1 * serverHandler.dispatch("[message 3]") >> { instant.repliesReceived }
        0 * _._
    }

    def "can wire three hubs together"() {
        def replies = new CountDownLatch(8)
        Dispatch<String> client1Handler = Mock()
        Dispatch<String> client2Handler = Mock()
        Dispatch<String> serverHandler = Mock()
        def server = server()
        def client1 = client(server)
        def client2 = client(server)
        server.hub.addHandler("channel", serverHandler)
        client1.hub.addHandler("channel", client1Handler)
        client2.hub.addHandler("channel", client2Handler)

        def serverDispatch = server.hub.getOutgoing("channel", String)
        def client1Dispatch = client1.hub.getOutgoing("channel", String)
        def client2Dispatch = client2.hub.getOutgoing("channel", String)

        when:
        serverDispatch.dispatch("message 1")
        serverDispatch.dispatch("message 2")
        serverDispatch.dispatch("message 3")
        serverDispatch.dispatch("message 4")
        serverDispatch.dispatch("message 5")
        serverDispatch.dispatch("message 6")
        serverDispatch.dispatch("message 7")
        serverDispatch.dispatch("message 8")
        replies.await()
        client1.stop()
        client2.stop()
        server.stop()

        then:
        _ * client1Handler.dispatch(_) >> { String message -> client1Dispatch.dispatch("[${message}]" as String) }
        _ * client2Handler.dispatch(_) >> { String message -> client2Dispatch.dispatch("[${message}]" as String) }
        1 * serverHandler.dispatch("[message 1]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 2]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 3]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 4]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 5]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 6]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 7]") >> { replies.countDown() }
        1 * serverHandler.dispatch("[message 8]") >> { replies.countDown() }
        0 * _._
    }

    def "each channel is independent"() {
        Dispatch<String> clientHandler1 = Mock()
        Dispatch<String> clientHandler2 = Mock()
        Dispatch<String> serverHandler = Mock()
        def server = server()
        def client = client(server)
        server.hub.addHandler("channel", serverHandler)
        client.hub.addHandler("channel1", clientHandler1)
        client.hub.addHandler("channel2", clientHandler2)

        def serverDispatch1 = server.hub.getOutgoing("channel1", String)
        def serverDispatch2 = server.hub.getOutgoing("channel2", String)
        def clientDispatch = client.hub.getOutgoing("channel", String)

        when:
        serverDispatch1.dispatch("message 1")
        serverDispatch1.dispatch("message 2")
        serverDispatch1.dispatch("message 3")
        serverDispatch1.dispatch("message 4")
        serverDispatch2.dispatch("message 5")
        serverDispatch2.dispatch("message 6")
        serverDispatch2.dispatch("message 7")
        serverDispatch2.dispatch("message 8")
        thread.blockUntil.channel1Done
        thread.blockUntil.channel2Done
        server.stop()
        client.stop()

        then:
        1 * clientHandler1.dispatch("message 1") >> { clientDispatch.dispatch("[message 1]") }
        1 * clientHandler1.dispatch("message 2") >> { clientDispatch.dispatch("[message 2]") }
        1 * clientHandler1.dispatch("message 3")
        1 * clientHandler1.dispatch("message 4") >> { clientDispatch.dispatch("[message 3]") }
        1 * clientHandler2.dispatch("message 5")
        1 * clientHandler2.dispatch("message 6")
        1 * clientHandler2.dispatch("message 7")
        1 * clientHandler2.dispatch("message 8") >> { clientDispatch.dispatch("[message 4]"); }
        1 * serverHandler.dispatch("[message 1]")
        1 * serverHandler.dispatch("[message 2]")
        1 * serverHandler.dispatch("[message 3]") >> { instant.channel1Done }
        1 * serverHandler.dispatch("[message 4]") >> { instant.channel2Done }
        0 * _._
    }

    def server() {
        return new Server()
    }

    def client(Server server) {
        return new Client(server.address)
    }

    private class Server {
        final MessageHub hub
        final Address address

        Server() {
            this.hub = new MessageHub("server", getExecutorFactory(), errorHandler)
            this.address = incomingConnector.accept({ event ->
                hub.addConnection(event.connection)
            } as Action, false)
        }

        def stop() {
            hub.stop()
        }
    }

    private class Client {
        final MessageHub hub
        final Connection connection

        Client(Address address) {
            this.hub = new MessageHub("client", getExecutorFactory(), errorHandler)
            this.connection = outgoingConnector.connect(address)
            hub.addConnection(connection)
        }

        def stop() {
            hub.stop()
            connection.stop()
        }
    }
}
