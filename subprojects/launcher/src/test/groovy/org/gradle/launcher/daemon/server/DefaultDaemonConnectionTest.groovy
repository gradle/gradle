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

import org.gradle.launcher.daemon.protocol.CloseInput
import org.gradle.launcher.daemon.protocol.ForwardInput
import org.gradle.launcher.daemon.server.exec.StdinHandler
import org.gradle.messaging.remote.internal.Connection
import org.gradle.util.ConcurrentSpecification

import java.util.concurrent.CountDownLatch
import org.gradle.launcher.daemon.protocol.Stop
import java.util.concurrent.TimeUnit

class DefaultDaemonConnectionTest extends ConcurrentSpecification {
    final TestConnection connection = new TestConnection()
    final DefaultDaemonConnection daemonConnection = new DefaultDaemonConnection(connection, executorFactory)

    def cleanup() {
        connection.disconnect()
        daemonConnection.stop()
    }

    def "forwards queued input events to stdin handler until end of input received"() {
        StdinHandler handler = Mock()
        def input1 = new ForwardInput(1, "hello".bytes)
        def input2 = new ForwardInput(2, "hello".bytes)
        def closeInput = new CloseInput(3)
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
        def input1 = new ForwardInput(1, "hello".bytes)
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
        def input1 = new ForwardInput(1, "hello".bytes)
        def input2 = new ForwardInput(2, "hello".bytes)
        def closeInput = new CloseInput(3)
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
        connection.queueIncoming(new ForwardInput(1, "hello".bytes))
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
        def input1 = new ForwardInput(1, "hello".bytes)
        def input2 = new ForwardInput(2, "hello".bytes)
        def closeInput = new CloseInput(3)
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
        result == ["incoming1", "incoming2", "incoming3"]
    }

    def "receive blocks until message available"() {
        def waiting = new CountDownLatch(1)
        def result = null

        when:
        start {
            waiting.countDown()
            result = daemonConnection.receive(20, TimeUnit.SECONDS)
        }
        waiting.await()
        Thread.sleep(500)
        connection.queueIncoming("incoming")
        daemonConnection.stop()
        finished()

        then:
        result == "incoming"
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
        connection.queueIncoming(new ForwardInput(12, "yo".bytes))
        connection.queueIncoming(new CloseInput(44))
        connection.queueIncoming("incoming2")
        def result = []
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        result << daemonConnection.receive(20, TimeUnit.SECONDS)
        daemonConnection.stop()

        then:
        result == ["incoming1", "incoming2"]
    }

    static class TestConnection implements Connection<Object> {
        final Object lock = new Object()
        final Object endInput = new Object()
        final LinkedList<Object> receiveQueue = new LinkedList<Object>()

        void requestStop() {
        }

        void dispatch(Object message) {
        }

        void queueIncoming(Object message) {
            synchronized (lock) {
                receiveQueue << message
                lock.notifyAll()
            }
        }

        void queueBroken(Throwable failure = new RuntimeException()) {
            queueIncoming(failure)
        }

        Object receive() {
            synchronized (lock) {
                while (receiveQueue.empty) {
                    lock.wait()
                }
                def message = receiveQueue.removeFirst()
                if (message instanceof Throwable) {
                    message.fillInStackTrace()
                    throw message
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
}
