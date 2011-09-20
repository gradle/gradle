/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.remote.internal

import org.gradle.messaging.concurrent.DefaultExecutorFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.CountDownLatch

import spock.lang.*
import spock.util.concurrent.*
import org.spockframework.runtime.SpockTimeoutError

class DisconnectAwareConnectionDecoratorTest extends Specification {

    def messageQueue = new LinkedBlockingQueue()

    def rawConnection = new Connection() {
        void stop() { disconnect() }
        void requestStop() { disconnect() }
        def receive() { 
            def val = messageQueue.take().first()
            if (val == null) {
                messageQueue.put([null])
            }
            val
        }
        void dispatch(message) {}
    }
    def connection
    
    def connection() {
        new DisconnectAwareConnectionDecorator(rawConnection, new DefaultExecutorFactory().create("test"))
    }

    void sendMessage(message = 1) {
        messageQueue.put([message])
    }

    void disconnect() {
        messageQueue.put([null])
    }

    def receive() {
        connection.receive()
    }

    def disconnectedHolder = new BlockingVariable(3) // allow 3 seconds for the disconnect handler to fire

    def onDisconnect(Closure action = { disconnectedHolder.set(true) }) {
        connection.onDisconnect(action)
    }

    boolean isDisconnectHandlerDidFire() {
        try {
            disconnectedHolder.get()
        } catch (SpockTimeoutError e) {
            false
        }
    }

    def setup() {
        connection = connection()
        onDisconnect() // install the default handler
    }

    def "normal send and receive"() {
        when:
        sendMessage(1)

        then:
        receive() == 1
    }

    def "disconnect after send and before message"() {
        when:
        sendMessage(1)

        and:
        disconnect()

        then:
        disconnectHandlerDidFire

        and:
        receive() == 1
    }

    def "disconnect before sending any messages"() {
        when:
        disconnect()

        then:
        disconnectHandlerDidFire

        and:
        receive() == null
    }

    def "stopping connection does not fire handler"() {
        given:
        sendMessage(1)
        sendMessage(2)

        when:
        sleep 1000 // wait for the messages to be consumed by the buffer
        connection.stop()

        then:
        receive() == 1
        receive() == 2
        receive() == null

        and:
        !disconnectHandlerDidFire
    }


    @Timeout(10)
    def "receive does not return null until disconnect handler set and complete"() {
        given:
        connection = connection() // default connection has the default disconnect handler, create a new one with no handler
        disconnect()
        
        def disconnectLatch = new CountDownLatch(1)
        def receiveLatch = new CountDownLatch(1)
        
        and:
        def received = []
        Thread.start { received << receive(); receiveLatch.countDown() }

        when:
        sleep 1000

        then:
        received.empty // receive() should be blocked, waiting for the disconnect handler

        when:
        onDisconnect { disconnectLatch.await(); }

        then:
        received.empty // receive() should still be blocked because the disconnect handler hasn't completed

        when:
        disconnectLatch.countDown()
        receiveLatch.await()

        then:
        received.size() == 1
        received[0] == null
    }

    def cleanup() {
        connection.stop()
    }
}