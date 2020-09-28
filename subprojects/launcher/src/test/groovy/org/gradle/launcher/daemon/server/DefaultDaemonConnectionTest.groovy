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

package org.gradle.launcher.daemon.server

import org.gradle.internal.remote.internal.MessageIOException
import org.gradle.internal.remote.internal.RemoteConnection
import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.protocol.Message
import org.gradle.launcher.daemon.server.api.StdinHandler
import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultDaemonConnectionTest extends ConcurrentSpecification {
    final TestConnection connection = new TestConnection()
    final DefaultDaemonConnection daemonConnection = new DefaultDaemonConnection(new SynchronizedDispatchConnection<Message>(connection), executorFactory)

    def cleanup() {
        connection.disconnect()
        daemonConnection.stop()
    }

    def "forwards queued input events to stdin handler until end of input received"() {
        StdinHandler handler = Mock()
        def input1 = new ForwardInput("hello".bytes)
        def input2 = new ForwardInput("hello".bytes)
        def closeInput = new CloseInput()
        def received = new CountDownLatch(1)

        when:
        daemonConnection.onStdin(handler)
        connection.queueIncoming(input1)
        connection.queueIncoming(input2)
        connection.queueIncoming(closeInput)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.onInput(input1)
        1 * handler.onInput(input2)
        1 * handler.onEndOfInput() >> { received.countDown() }
        0 * handler._
    }

    def "generates end of stdin event when connection disconnects"() {
        StdinHandler handler = Mock()
        def input1 = new ForwardInput("hello".bytes)
        def received = new CountDownLatch(1)

        when:
        daemonConnection.onStdin(handler)
        connection.queueIncoming(input1)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.onInput(input1) >> { received.countDown() }
        1 * handler.onEndOfInput()
        0 * handler._
    }

    def "generates end of stdin event when connection stopped"() {
        StdinHandler handler = Mock()

        when:
        daemonConnection.onStdin(handler)
        daemonConnection.stop()

        then:
        1 * handler.onEndOfInput()
        0 * handler._
    }

    def "buffers stdin events"() {
        StdinHandler handler = Mock()
        def input1 = new ForwardInput("hello".bytes)
        def input2 = new ForwardInput("hello".bytes)
        def closeInput = new CloseInput()
        def received = new CountDownLatch(1)

        when:
        connection.queueIncoming(input1)
        connection.queueIncoming(input2)
        connection.queueIncoming(closeInput)
        daemonConnection.onStdin(handler)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.onInput(input1)
        1 * handler.onInput(input2)
        1 * handler.onEndOfInput() >> { received.countDown() }
        0 * handler._
    }

    def "does not notify stdin handler once it is removed"() {
        StdinHandler handler = Mock()

        when:
        daemonConnection.onStdin(handler)
        daemonConnection.onStdin(null)
        connection.disconnect()
        daemonConnection.stop()

        then:
        0 * handler._
    }

    def "discards queued messages on stop"() {
        when:
        connection.queueIncoming("incoming")
        connection.queueIncoming(new ForwardInput("hello".bytes))
        connection.disconnect()
        daemonConnection.stop()

        then:
        notThrown()
    }

    def "handles case where cannot receive from connection"() {
        when:
        connection.queueBroken()
        daemonConnection.stop()

        then:
        notThrown()
    }

    def "handles failure to notify stdin handler"() {
        StdinHandler handler = Mock()
        def input1 = new ForwardInput("hello".bytes)
        def input2 = new ForwardInput("hello".bytes)
        def closeInput = new CloseInput()
        def received = new CountDownLatch(1)

        when:
        connection.queueIncoming(input1)
        connection.queueIncoming(input2)
        connection.queueIncoming(closeInput)
        daemonConnection.onStdin(handler)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.onInput(input1) >> { received.countDown(); throw new RuntimeException() }
        0 * handler._
    }

    def "notifies disconnect handler on disconnect"() {
        Runnable handler = Mock()
        def received = new CountDownLatch(1)

        when:
        daemonConnection.onDisconnect(handler)
        connection.disconnect()
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.run() >> { received.countDown() }
        0 * handler._
    }

    def "notifies disconnect handler when already disconnected"() {
        Runnable handler = Mock()
        def received = new CountDownLatch(1)

        when:
        connection.disconnect()
        daemonConnection.onDisconnect(handler)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.run() >> { received.countDown() }
        0 * handler._
    }

    def "does not notify disconnect handler once it has been removed"() {
        Runnable handler = Mock()

        when:
        daemonConnection.onDisconnect(handler)
        daemonConnection.onDisconnect(null)
        connection.disconnect()
        daemonConnection.stop()

        then:
        0 * handler._
    }

    def "does not notify disconnect handler on stop"() {
        Runnable handler = Mock()

        when:
        daemonConnection.onDisconnect(handler)
        daemonConnection.stop()

        then:
        0 * handler._
    }

    def "can stop after disconnect handler fails"() {
        Runnable handler = Mock()
        def received = new CountDownLatch(1)

        when:
        connection.disconnect()
        daemonConnection.onDisconnect(handler)
        received.await()
        daemonConnection.stop()

        then:
        1 * handler.run() >> { received.countDown(); throw new RuntimeException() }
        0 * handler._
    }

    def "receive queues incoming messages"() {
        when:
        connection.queueIncoming("incoming1")
        connection.queueIncoming("incoming2")
        connection.queueIncoming("incoming3")
        def result = []
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        daemonConnection.stop()

        then:
        result*.message == ["incoming1", "incoming2", "incoming3"]
    }

    def "receive blocks until message available"() {
        def waiting = new CountDownLatch(1)
        def received = new CountDownLatch(1)
        def result = null

        when:
        start {
            waiting.countDown()
            result = daemonConnection.receive(20, TimeUnit.SECONDS)
            received.countDown()
        }
        waiting.await()
        Thread.sleep(500)
        connection.queueIncoming("incoming")
        received.await()

        then:
        result instanceof Received
        result.message == "incoming"
    }

    def "receive blocks until connection stopped"() {
        def waiting = new CountDownLatch(1)
        def result = null

        when:
        start {
            waiting.countDown()
            result = daemonConnection.receive(20, TimeUnit.SECONDS)
        }
        waiting.await()
        Thread.sleep(500)
        daemonConnection.stop()
        finished()

        then:
        result == null
    }

    def "receive blocks until connection disconnected"() {
        def waiting = new CountDownLatch(1)
        def result = null

        when:
        start {
            waiting.countDown()
            result = daemonConnection.receive(20, TimeUnit.SECONDS)
        }
        waiting.await()
        Thread.sleep(500)
        connection.disconnect()
        finished()

        then:
        result == null
    }

    def "receive blocks until timeout"() {
        when:
        def result = daemonConnection.receive(100, TimeUnit.MILLISECONDS)

        then:
        result == null
    }

    def "receive rethrows failure to receive from connection"() {
        def waiting = new CountDownLatch(1)
        def failure = new RuntimeException()
        def result = null

        when:
        start {
            waiting.countDown()
            try {
                daemonConnection.receive(20, TimeUnit.SECONDS)
            } catch (RuntimeException e) {
                result = e
            }
        }
        waiting.await()
        Thread.sleep(500)
        connection.queueBroken(failure)
        finished()

        then:
        result == failure
    }

    def "receive ignores stdin messages"() {
        when:
        connection.queueIncoming("incoming1")
        connection.queueIncoming(new ForwardInput("yo".bytes))
        connection.queueIncoming(new CloseInput())
        connection.queueIncoming("incoming2")
        def result = []
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        daemonConnection.stop()

        then:
        result*.message == ["incoming1", "incoming2"]
    }

    static class TestConnection implements RemoteConnection<Message> {
        private final def lock = new Object()
        private final def endInput = new Received("end")
        private final def receiveQueue = new LinkedList<Message>()

        @Override
        void dispatch(Message message) throws MessageIOException {
            throw new UnsupportedOperationException()
        }

        @Override
        void flush() throws MessageIOException {
            throw new UnsupportedOperationException()
        }

        void queueIncoming(String message) {
            queueIncoming(new Received(message))
        }

        void queueIncoming(Message message) {
            synchronized (lock) {
                receiveQueue << message
                lock.notifyAll()
            }
        }

        void queueBroken(Throwable failure = new RuntimeException()) {
            queueIncoming(new Failure(failure))
        }

        Message receive() {
            synchronized (lock) {
                while (receiveQueue.empty) {
                    lock.wait()
                }
                def message = receiveQueue.removeFirst()
                if (message instanceof Failure) {
                    message.failure.fillInStackTrace()
                    throw message.failure
                }
                return message == endInput ? null : message
            }
        }

        void stop() {
            disconnect()
        }

        void disconnect() {
            queueIncoming(endInput)
        }
    }

    private static class Failure extends Message {
        final Throwable failure

        Failure(Throwable failure) {
            this.failure = failure
        }
    }

    private static class Received extends Message {
        final String message

        Received(String message) {
            this.message = message
        }
    }
}
