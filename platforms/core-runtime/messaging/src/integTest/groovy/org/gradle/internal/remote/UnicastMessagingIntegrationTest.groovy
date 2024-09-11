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

package org.gradle.internal.remote

import org.gradle.api.Action
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.remote.services.MessagingServices
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock

@Timeout(60)
class UnicastMessagingIntegrationTest extends ConcurrentSpec {
    def "server can send messages to client"() {
        RemoteService1 service = Mock()
        def server = new Server()
        def client = new Client(server.address)

        given:
        client.addIncoming(service)
        server.setupOutgoingService1()
        server.setupOutgoingService2()

        and:
        client.connection.connect()
        server.connection.connect()


        when:
        server.outgoingService1.doStuff("1")
        server.outgoingService1.doStuff("2")
        server.outgoingService1.doStuff("3")
        server.outgoingService1.doStuff("4")
        thread.blockUntil.received
        server.stop()
        client.stop()

        then:
        1 * service.doStuff("1")
        1 * service.doStuff("2")
        1 * service.doStuff("3")
        1 * service.doStuff("4") >> { instant.received }

        cleanup:
        client?.stop()
        server?.stop()
    }

    def "client can send messages to server"() {
        RemoteService1 service = Mock()
        def server = new Server()
        def client = new Client(server.address)

        given:
        server.addIncoming(service)
        client.setupOutgoingService1()

        and:
        server.connection.connect()
        client.connection.connect()

        when:
        client.outgoingService1.doStuff("1")
        client.outgoingService1.doStuff("2")
        client.outgoingService1.doStuff("3")
        client.outgoingService1.doStuff("4")
        thread.blockUntil.received
        client.stop()
        server.stop()

        then:
        1 * service.doStuff("1")
        1 * service.doStuff("2")
        1 * service.doStuff("3")
        1 * service.doStuff("4") >> { instant.received }

        cleanup:
        client?.stop()
        server?.stop()
    }

    def "server and client can both send same type of message"() {
        RemoteService1 serverService = Mock()
        RemoteService1 clientService = Mock()
        def received = new CountDownLatch(2)
        def server = new Server()
        def client = new Client(server.address)

        when:
        start {
            server.addIncoming(serverService)
            server.setupOutgoingService1()
            server.setupOutgoingService2()
            server.connection.connect()
            server.outgoingService1.doStuff("from server 1")
            server.outgoingService1.doStuff("from server 2")
        }
        start {
            client.addIncoming(clientService)
            client.setupOutgoingService1()
            client.setupOutgoingService2()
            client.connection.connect()
            client.outgoingService1.doStuff("from client 1")
            client.outgoingService1.doStuff("from client 2")
        }
        received.await()
        client.stop()
        server.stop()

        then:
        1 * serverService.doStuff("from client 1")
        1 * serverService.doStuff("from client 2") >> { received.countDown() }
        1 * clientService.doStuff("from server 1")
        1 * clientService.doStuff("from server 2") >> { received.countDown() }

        cleanup:
        client?.stop()
        server?.stop()
    }

    def "client and server can each send different types of messages"() {
        RemoteService1 serverService = Mock()
        RemoteService2 clientService = Mock()
        def done = new CountDownLatch(2)
        def server = new Server()
        def client = new Client(server.address)

        when:
        start {
            server.addIncoming(serverService)
            server.setupOutgoingService2()
            server.connection.connect()
            server.outgoingService2.doStuff("server1")
            server.outgoingService2.doStuff("server2")
        }
        start {
            client.addIncoming(clientService)
            client.setupOutgoingService1()
            client.connection.connect()
            client.outgoingService1.doStuff("client1")
            client.outgoingService1.doStuff("client2")
        }
        done.await()
        client.stop()
        server.stop()

        then:
        1 * serverService.doStuff("client1")
        1 * serverService.doStuff("client2") >> { done.countDown() }
        1 * clientService.doStuff("server1")
        1 * clientService.doStuff("server2") >> { done.countDown() }

        cleanup:
        client?.stop()
        server?.stop()
    }

    def "client can start sending before server has started"() {
        RemoteService1 service = Mock()
        def server = new Server()
        def client

        when:
        client = new Client(server.address)
        client.setupOutgoingService1()
        client.connection.connect()
        client.outgoingService1.doStuff("1")
        client.outgoingService1.doStuff("2")

        server.addIncoming(service)
        server.connection.connect()
        thread.blockUntil.received
        server.stop()
        client?.stop()

        then:
        1 * service.doStuff("1")
        1 * service.doStuff("2") >> { instant.received }

        cleanup:
        client?.stop()
        server?.stop()
    }

    abstract class Participant {
        RemoteService1 outgoingService1
        RemoteService2 outgoingService2

        abstract ObjectConnection getConnection()

        void addIncoming(RemoteService1 value) {
            connection.addIncoming(RemoteService1.class, value)
        }

        void addIncoming(RemoteService2 value) {
            connection.addIncoming(RemoteService2.class, value)
        }

        void setupOutgoingService1(){
            outgoingService1 = connection.addOutgoing(RemoteService1)
        }

        void setupOutgoingService2(){
            outgoingService2 = connection.addOutgoing(RemoteService2)
        }
        abstract void stop()
    }

    class Server extends Participant {
        private final lock = new ReentrantLock()
        private final condition = lock.newCondition()
        private final services = ServiceRegistryBuilder.builder().provider(new TestMessagingServices()).build()
        private ConnectionAcceptor acceptor
        private ObjectConnection connection
        final Address address

        Server() {
            def server = services.get(MessagingServer)
            acceptor = server.accept({ event ->
                lock.lock()
                try {
                    connection = event
                    condition.signalAll()
                } finally {
                    lock.unlock()
                }
            } as Action)
            address = acceptor.address
        }

        @Override
        ObjectConnection getConnection() {
            lock.lock()
            try {
                while (connection == null) {
                    condition.await()
                }
                return connection
            } finally {
                lock.unlock()
            }
        }

        @Override
        void stop() {
            lock.lock()
            try {
                CompositeStoppable.stoppable(acceptor, connection).stop()
            } finally {
                connection = null
                acceptor = null
                lock.unlock()
            }
            services.close()
        }
    }

    class Client extends Participant {
        final ObjectConnection connection
        final services = ServiceRegistryBuilder.builder().provider(new TestMessagingServices()).build()

        Client(Address serverAddress) {
            def client = services.get(MessagingClient)
            connection = client.getConnection(serverAddress)
        }

        @Override
        void stop() {
            connection?.stop()
            services.close()
        }
    }

    class TestMessagingServices extends MessagingServices {
        @Provides
        protected ExecutorFactory createExecutorFactory() {
            return executorFactory
        }
    }
}

interface RemoteService1 {
    def doStuff(String value)
}

interface RemoteService2 {
    def doStuff(String value)
}
