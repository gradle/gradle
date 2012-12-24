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
import org.gradle.messaging.dispatch.MethodInvocation
import org.gradle.messaging.dispatch.ProxyDispatchAdapter
import org.gradle.messaging.dispatch.ReflectionDispatch
import org.gradle.messaging.remote.Address
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import org.gradle.messaging.remote.internal.inet.InetAddressFactory
import org.gradle.messaging.remote.internal.inet.TcpIncomingConnector
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector
import org.gradle.messaging.serialize.kryo.TypeSafeKryoAwareSerializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch

@Timeout(60)
class MessageHubIntegrationTest extends ConcurrentSpec {
    final serializer = new InterHubMessageSerializer(new TypeSafeKryoAwareSerializer<MethodInvocation>(MethodInvocation.class, new MethodInvocationSerializer(getClass().classLoader)))
    final outgoingConnector = new TcpOutgoingConnector<InterHubMessage>(serializer)
    final incomingConnector = new TcpIncomingConnector<InterHubMessage>(executorFactory, serializer, new InetAddressFactory(), new UUIDGenerator())
    final Action<Throwable> errorHandler = Mock()

    def cleanup() {
        incomingConnector?.stop()
    }

    def "can wire two hubs together"() {
        TestService clientHandler = Mock()
        TestService serverHandler = Mock()
        def server = server()
        def client = client(server)
        server.addHandler("channel", serverHandler)
        client.addHandler("channel", clientHandler)

        def serverDispatch = server.createOutgoing("channel")
        def clientDispatch = client.createOutgoing("channel")

        when:
        serverDispatch.doStuff("message 1")
        serverDispatch.doStuff("message 2")
        serverDispatch.doStuff("message 3")
        serverDispatch.doStuff("message 4")
        serverDispatch.doStuff("message 5")
        serverDispatch.doStuff("message 6")
        serverDispatch.doStuff("message 7")
        thread.blockUntil.repliesReceived
        server.stop()
        client.stop()

        then:
        1 * clientHandler.doStuff("message 1") >> { clientDispatch.doStuff("[message 1]") }
        1 * clientHandler.doStuff("message 2") >> { clientDispatch.doStuff("[message 2]") }
        1 * clientHandler.doStuff("message 3")
        1 * clientHandler.doStuff("message 4")
        1 * clientHandler.doStuff("message 5")
        1 * clientHandler.doStuff("message 6")
        1 * clientHandler.doStuff("message 7") >> { clientDispatch.doStuff("[message 3]"); }
        1 * serverHandler.doStuff("[message 1]")
        1 * serverHandler.doStuff("[message 2]")
        1 * serverHandler.doStuff("[message 3]") >> { instant.repliesReceived }
        0 * _._
    }

    def "can wire three hubs together"() {
        def replies = new CountDownLatch(8)
        TestService client1Handler = Mock()
        TestService client2Handler = Mock()
        TestService serverHandler = Mock()
        def server = server()
        def client1 = client(server)
        def client2 = client(server)
        server.addHandler("channel", serverHandler)
        client1.addHandler("channel", client1Handler)
        client2.addHandler("channel", client2Handler)

        def serverDispatch = server.createOutgoing("channel")
        def client1Dispatch = client1.createOutgoing("channel")
        def client2Dispatch = client2.createOutgoing("channel")

        when:
        serverDispatch.doStuff("message 1")
        serverDispatch.doStuff("message 2")
        serverDispatch.doStuff("message 3")
        serverDispatch.doStuff("message 4")
        serverDispatch.doStuff("message 5")
        serverDispatch.doStuff("message 6")
        serverDispatch.doStuff("message 7")
        serverDispatch.doStuff("message 8")
        replies.await()
        client1.stop()
        client2.stop()
        server.stop()

        then:
        _ * client1Handler.doStuff(_) >> { String message -> client1Dispatch.doStuff("[${message}]") }
        _ * client2Handler.doStuff(_) >> { String message -> client2Dispatch.doStuff("[${message}]") }
        1 * serverHandler.doStuff("[message 1]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 2]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 3]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 4]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 5]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 6]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 7]") >> { replies.countDown() }
        1 * serverHandler.doStuff("[message 8]") >> { replies.countDown() }
        0 * _._
    }

    def "each channel is independent"() {
        TestService clientHandler1 = Mock()
        TestService clientHandler2 = Mock()
        TestService serverHandler = Mock()
        def server = server()
        def client = client(server)
        server.addHandler("channel", serverHandler)
        client.addHandler("channel1", clientHandler1)
        client.addHandler("channel2", clientHandler2)

        def serverDispatch1 = server.createOutgoing("channel1")
        def serverDispatch2 = server.createOutgoing("channel2")
        def clientDispatch = client.createOutgoing("channel")

        when:
        serverDispatch1.doStuff("message 1")
        serverDispatch1.doStuff("message 2")
        serverDispatch1.doStuff("message 3")
        serverDispatch1.doStuff("message 4")
        serverDispatch2.doStuff("message 5")
        serverDispatch2.doStuff("message 6")
        serverDispatch2.doStuff("message 7")
        serverDispatch2.doStuff("message 8")
        thread.blockUntil.channel1Done
        thread.blockUntil.channel2Done
        server.stop()
        client.stop()

        then:
        1 * clientHandler1.doStuff("message 1") >> { clientDispatch.doStuff("[message 1]") }
        1 * clientHandler1.doStuff("message 2") >> { clientDispatch.doStuff("[message 2]") }
        1 * clientHandler1.doStuff("message 3")
        1 * clientHandler1.doStuff("message 4") >> { clientDispatch.doStuff("[message 3]") }
        1 * clientHandler2.doStuff("message 5")
        1 * clientHandler2.doStuff("message 6")
        1 * clientHandler2.doStuff("message 7")
        1 * clientHandler2.doStuff("message 8") >> { clientDispatch.doStuff("[message 4]"); }
        1 * serverHandler.doStuff("[message 1]")
        1 * serverHandler.doStuff("[message 2]")
        1 * serverHandler.doStuff("[message 3]") >> { instant.channel1Done }
        1 * serverHandler.doStuff("[message 4]") >> { instant.channel2Done }
        0 * _._
    }

    def server() {
        return new Server()
    }

    def client(Server server) {
        return new Client(server.address)
    }

    private class Participant {
        MessageHub hub

        TestService createOutgoing(String channel) {
            def dispatch = new ProxyDispatchAdapter<TestService>(hub.getOutgoing(channel, MethodInvocation), TestService)
            return dispatch.source
        }

        void addHandler(String channel, TestService handler) {
            hub.addHandler(channel, new ReflectionDispatch(handler))
        }

        def stop() {
            hub.stop()
        }
    }

    private class Server extends Participant {
        final Address address

        Server() {
            hub = new MessageHub("server", getExecutorFactory(), errorHandler)
            this.address = incomingConnector.accept({ event ->
                hub.addConnection(event.connection)
            } as Action, false)
        }
    }

    private class Client extends Participant {
        final Connection connection

        Client(Address address) {
            hub = new MessageHub("client", getExecutorFactory(), errorHandler)
            this.connection = outgoingConnector.connect(address)
            hub.addConnection(connection)
        }

        def stop() {
            hub.stop()
            connection.stop()
        }
    }

    interface TestService {
        void doStuff(String param)
    }
}
