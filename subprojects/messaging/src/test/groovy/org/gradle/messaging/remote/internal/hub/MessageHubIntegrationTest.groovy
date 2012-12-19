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
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.remote.internal.Connection
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class MessageHubIntegrationTest extends ConcurrentSpec {
    def "can wire two hubs together"() {
        Action<Throwable> errorHandler = Mock()
        Dispatch<String> handlerB = Mock()
        Dispatch<String> handlerA = Mock()
        def connector = new InMemoryConnector()
        def hubA = new MessageHub("hub A", executorFactory, errorHandler)
        def hubB = new MessageHub("hub B", executorFactory, errorHandler)
        hubA.addHandler("channel", handlerA)
        hubB.addHandler("channel", handlerB)
        hubA.addConnection(connector.connectionA)
        hubB.addConnection(connector.connectionB)
        def dispatchA = hubA.getOutgoing("channel", String)
        def dispatchB = hubB.getOutgoing("channel", String)

        when:
        dispatchA.dispatch("message 1")
        dispatchA.dispatch("message 2")
        thread.blockUntil.repliesReceived
        hubA.stop()
        hubB.stop()

        then:
        1 * handlerB.dispatch("message 1") >> { dispatchB.dispatch("[message 1]") }
        1 * handlerB.dispatch("message 2") >> { dispatchB.dispatch("[message 2]") }
        1 * handlerA.dispatch("[message 1]")
        1 * handlerA.dispatch("[message 2]") >> { instant.repliesReceived }
        0 * _._
    }

    static class InMemoryConnector {
        private final BlockingQueue<InterHubMessage> connectionAIncoming = new LinkedBlockingQueue<>()
        private final BlockingQueue<InterHubMessage> connectionBIncoming = new LinkedBlockingQueue<>()

        Connection<InterHubMessage> getConnectionA() {
            return new Connection<InterHubMessage>() {
                @Override
                String toString() {
                    return "[connection A]"
                }

                void dispatch(InterHubMessage message) {
                    connectionBIncoming.put(message)
                }

                InterHubMessage receive() {
                    return connectionAIncoming.take()
                }

                void requestStop() {
                    throw new UnsupportedOperationException()
                }

                void stop() {
                    throw new UnsupportedOperationException()
                }
            }
        }

        Connection<InterHubMessage> getConnectionB() {
            return new Connection<InterHubMessage>() {
                @Override
                String toString() {
                    return "[connection B]"
                }

                void dispatch(InterHubMessage message) {
                    connectionAIncoming.put(message)
                }

                InterHubMessage receive() {
                    return connectionBIncoming.take()
                }

                void requestStop() {
                    throw new UnsupportedOperationException()
                }

                void stop() {
                    throw new UnsupportedOperationException()
                }
            }
        }
    }
}
