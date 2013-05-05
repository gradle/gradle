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

package org.gradle.messaging.remote

import org.gradle.messaging.remote.internal.IncomingBroadcast
import org.gradle.messaging.remote.internal.MessagingServices
import org.gradle.messaging.remote.internal.OutgoingBroadcast
import org.gradle.messaging.remote.internal.inet.SocketInetAddress
import org.gradle.util.ConcurrentSpecification
import spock.lang.Ignore

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Ignore
class BroadcastMessagingIntegrationTest extends ConcurrentSpecification {
    static final Random RANDOM = new Random()
    final def testGroup = "test-group-${RANDOM.nextLong()}"
    final def address = new SocketInetAddress(InetAddress.getByName("233.253.17.122"), 7914)

    def "client can discover and send messages to server"() {
        TestService incoming = Mock()
        def server = newServer()
        def client = newClient()
        def discovered = startsAsyncAction()

        given:
        server.addIncoming(incoming)

        when:
        discovered.started {
            client.outgoing.doStuff("message")
        }

        then:
        1 * incoming.doStuff("message") >> { discovered.done() }

        cleanup:
        server?.stop()
        client?.stop()
    }

    def "multiple clients can discover server"() {
        TestService incoming = Mock()
        def server = newServer()
        def client1 = newClient()
        def client2 = newClient()
        def discovered1 = startsAsyncAction()
        def discovered2 = startsAsyncAction()

        given:
        server.addIncoming(incoming)

        when:
        discovered1.started {
            client1.outgoing.doStuff("client1")
        }
        discovered2.started {
            client2.outgoing.doStuff("client2")
        }

        then:
        1 * incoming.doStuff("client1") >> { discovered1.done() }
        1 * incoming.doStuff("client2") >> { discovered2.done() }

        cleanup:
        server?.stop()
        client1?.stop()
        client2?.stop()
    }

    @Ignore
    def "client can broadcast to multiple servers"() {
        TestService incoming1 = Mock()
        TestService incoming2 = Mock()
        def server1 = newServer()
        def server2 = newServer()
        def client = newClient()
        def broadcast = new CountDownLatch(2)

        given:
        server1.addIncoming(incoming1)
        server2.addIncoming(incoming2)

        when:
        start {
            client.outgoing.doStuff("message")
            broadcast.await()
        }
        finished()

        then:
        1 * incoming1.doStuff("message") >> { broadcast.countDown() }
        1 * incoming2.doStuff("message") >> { broadcast.countDown() }

        cleanup:
        server1?.stop()
        server2?.stop()
        client?.stop()
    }

    def "client stop flushes messages"() {
        TestService incoming = Mock()
        def client
        def server = newServer()

        given:
        server.addIncoming(incoming)

        when:
        start {
            client = newClient()
            client.outgoing.doStuff("message1")
            client.outgoing.doStuff("message2")
            client.outgoing.doStuff("message3")
            client.stop()
        }
        finished()

        then:
        1 * incoming.doStuff("message1")
        1 * incoming.doStuff("message2")
        1 * incoming.doStuff("message3")

        cleanup:
        client?.stop()
        server?.stop()
    }

    def "client can start broadcasting before server started"() {
        TestService incoming = Mock()
        def client = newClient()
        def server
        def discovered = startsAsyncAction()

        when:
        discovered.started {
            client.outgoing.doStuff("message")
            server = newServer()
            server.addIncoming(incoming)
        }

        then:
        1 * incoming.doStuff("message") >> { discovered.done() }

        cleanup:
        server?.stop()
        client?.stop()
    }

    def "client can start broadcasting after server started"() {
        TestService incoming = Mock()
        def client
        def server = newServer()
        def discovered = startsAsyncAction()

        given:
        server.addIncoming(incoming)

        when:
        discovered.started {
            client = newClient()
            client.outgoing.doStuff("message")
        }

        then:
        1 * incoming.doStuff("message") >> { discovered.done() }

        cleanup:
        server?.stop()
        client?.stop()
    }

    def "client can discover restarted server"() {
        TestService incoming1 = Mock()
        TestService incoming2 = Mock()
        def server1 = newServer()
        def server2
        def client = newClient()
        def discovered1 = startsAsyncAction()
        def discovered2 = startsAsyncAction()

        given:
        server1.addIncoming(incoming1)

        when:
        discovered1.started {
            client.outgoing.doStuff("message1")
        }

        then:
        1 * incoming1.doStuff("message1") >> { discovered1.done() }

        when:
        discovered2.started {
            server1.stop()
            server2 = newServer()
            server2.addIncoming(incoming2)
            client.outgoing.doStuff("message2")
        }

        then:
        1 * incoming2.doStuff("message2") >> { discovered2.done() }

        cleanup:
        client?.stop()
        server1?.stop()
        server2?.stop()
    }

    def "client can stop when no server has been discovered"() {
        def client = newClient()

        when:
        start {
            client.outgoing.doStuff("message1")
            client.stop()
        }.completesWithin(6, TimeUnit.SECONDS)

        then:
        notThrown(RuntimeException)

        cleanup:
        client?.stop()
    }

    def "can stop client when it has not sent any messages"() {
        def client = newClient()

        when:
        start {
            client.stop()
        }
        finished()

        then:
        notThrown(RuntimeException)

        cleanup:
        client?.stop()
    }

    def "groups are independent"() {
        def server1 = newServer("${testGroup}-1")
        def server2 = newServer("${testGroup}-2")
        def client1 = newClient("${testGroup}-1")
        def client2 = newClient("${testGroup}-2")
        def received = new CountDownLatch(2)
        TestService incoming1 = Mock()
        TestService incoming2 = Mock()

        given:
        server1.addIncoming(incoming1)
        server2.addIncoming(incoming2)

        when:
        start {
            client1.outgoing.doStuff("client1")
            client2.outgoing.doStuff("client2")
            client1.stop()
            client2.stop()
            server1.stop()
            server2.stop()
        }
        finished()

        then:
        1 * incoming1.doStuff("client1") >> { received.countDown() }
        1 * incoming2.doStuff("client2") >> { received.countDown() }
        0 * incoming1._
        0 * incoming2._

        cleanup:
        server1?.stop()
        server2?.stop()
        client1?.stop()
        client2?.stop()
    }

    private Client newClient(String group = testGroup) {
        return new Client(group)
    }

    private Server newServer(String group = testGroup) {
        return new Server(group)
    }


    class Server {
        final def services
        final def broadcast

        Server(String group) {
            services = new MessagingServices(getClass().classLoader, group, address)
            broadcast = services.get(IncomingBroadcast.class)
        }

        void addIncoming(TestService value) {
            broadcast.addIncoming(TestService.class, value)
        }

        void stop() {
            services.stop()
        }
    }

    class Client {
        final def services
        final def lookup
        final TestService outgoing

        Client(String group) {
            services = new MessagingServices(getClass().classLoader, group, address)
            lookup = services.get(OutgoingBroadcast.class)
            outgoing = lookup.addOutgoing(TestService)
        }

        void stop() {
            services.stop()
        }
    }
}

interface TestService {
    void doStuff(String param)
}
