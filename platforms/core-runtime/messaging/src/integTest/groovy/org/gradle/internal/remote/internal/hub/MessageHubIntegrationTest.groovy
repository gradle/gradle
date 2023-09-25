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

package org.gradle.internal.remote.internal.hub

import org.gradle.api.Action
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Timeout

import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

@Timeout(60)
class MessageHubIntegrationTest extends ConcurrentSpec {
    final Action<Throwable> errorHandler = Mock()

    def "can wire two hubs together"() {
        Dispatch<String> clientHandler = Mock()
        Dispatch<String> serverHandler = Mock()
        def server = new Participant()
        def client = new Participant()
        client.connectTo(server)
        server.addHandler("channel", serverHandler)
        client.addHandler("channel", clientHandler)

        def serverDispatch = server.createOutgoing("channel")
        def clientDispatch = client.createOutgoing("channel")

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
        def server = new Participant()
        def client1 = new Participant()
        def client2 = new Participant()
        client1.connectTo(server)
        client2.connectTo(server)
        server.addHandler("channel", serverHandler)
        client1.addHandler("channel", client1Handler)
        client2.addHandler("channel", client2Handler)

        def serverDispatch = server.createOutgoing("channel")
        def client1Dispatch = client1.createOutgoing("channel")
        def client2Dispatch = client2.createOutgoing("channel")

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
        def server = new Participant()
        def client = new Participant()
        client.connectTo(server)
        server.addHandler("channel", serverHandler)
        client.addHandler("channel1", clientHandler1)
        client.addHandler("channel2", clientHandler2)

        def serverDispatch1 = server.createOutgoing("channel1")
        def serverDispatch2 = server.createOutgoing("channel2")
        def clientDispatch = client.createOutgoing("channel")

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

    private class Participant {
        MessageHub hub = new MessageHub("participant", executorFactory, getErrorHandler())

        Dispatch<String> createOutgoing(String channel) {
            return hub.getOutgoing(channel, String)
        }

        void addHandler(String channel, Dispatch<String> handler) {
            hub.addHandler(channel, handler)
        }

        def connectTo(Participant other) {
            def connector = new TestConnector()
            hub.addConnection(connector.connectionA)
            other.hub.addConnection(connector.connectionB)
        }

        def stop() {
            hub.stop()
        }
    }

    private class TestConnector {
        private final BlockingQueue<InterHubMessage> incomingA = new LinkedBlockingQueue<>()
        private final BlockingQueue<InterHubMessage> outgoingA = new LinkedBlockingQueue<>()
        private final BlockingQueue<InterHubMessage> incomingB = new LinkedBlockingQueue<>()
        private final BlockingQueue<InterHubMessage> outgoingB = new LinkedBlockingQueue<>()

        RemoteConnection<InterHubMessage> getConnectionA() {
            return new RemoteConnection<InterHubMessage>() {
                void dispatch(InterHubMessage message) {
                    outgoingA.put(message)
                }

                @Override
                void flush() {
                    outgoingA.drainTo(incomingB)
                }

                InterHubMessage receive() {
                    return incomingA.take()
                }

                void stop() {
                    throw new UnsupportedOperationException()
                }
            }
        }

        RemoteConnection<InterHubMessage> getConnectionB() {
            return new RemoteConnection<InterHubMessage>() {
                void dispatch(InterHubMessage message) {
                    outgoingB.put(message)
                }

                @Override
                void flush()  {
                    outgoingB.drainTo(incomingA)
                }

                InterHubMessage receive() {
                    return incomingB.take()
                }

                void stop() {
                    throw new UnsupportedOperationException()
                }
            }
        }
    }
}
