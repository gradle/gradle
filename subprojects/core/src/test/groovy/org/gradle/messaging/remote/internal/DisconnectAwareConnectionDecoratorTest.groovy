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

import org.gradle.api.Action
import java.util.concurrent.LinkedBlockingQueue

import spock.lang.*
import spock.util.concurrent.*
import org.spockframework.runtime.SpockTimeoutError

class DisconnectAwareConnectionDecoratorTest extends Specification {

    def messageQueue = new LinkedBlockingQueue()

    def rawConnection = new Connection() {
        void stop() { disconnect() }
        void requestStop() { disconnect() }
        def receive() { messageQueue.take().first() }
        void dispatch(message) {}
    }

    def connection = new DisconnectAwareConnectionDecorator(rawConnection)

    void sendMessage(message = 1) {
        messageQueue.put([message])
    }

    void disconnect() {
        messageQueue.put([null])
    }

    def receive() {
        connection.receive()
    }

    def uncollectedMessagesHolder = new BlockingVariable(3) // allow 3 seconds for the disconnect handler to fire

    def onDisconnect(Closure action = { uncollectedMessagesHolder.set(it.uncollectedMessages) }) {
        connection.onDisconnect(action as Action)
    }

    def getUncollectedMessages() {
        try {
            uncollectedMessagesHolder.get()
        } catch (SpockTimeoutError e) {
            null
        }
    }

    def setup() {
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
        uncollectedMessages == [1]

        and:
        receive() == null
    }

    def "disconnect before sending any messages"() {
        when:
        disconnect()

        then:
        uncollectedMessages == []

        and:
        receive() == null
    }

    def "stop while there are unreceived messages"() {
        given:
        sendMessage(1)
        sendMessage(2)

        when:
        connection.stop()

        then:
        receive() == 1
        receive() == 2
        receive() == null

        and:
        uncollectedMessages == null // i.e. disconnect handler never fires
    }

    def "received + uncollected at time of disconnection is all of the messages"() {
        given:
        def toSend = 1..100
        def receieved = new BlockingVariable(3)

        and:
        Thread.start {
            def messages = []
            while (true) {
                def message = receive()
                if (message == null) {
                    break
                } else {
                    messages << message
                }
                sleep 100 // to ensure messages are being sent faster than received
            }
            receieved.set(messages)
        }

        when:
        Thread.start {
            toSend.each { sendMessage(it) }
            sleep 2000 // wait for some messages to be received
            disconnect()
        }

        then:
        receieved.get() + uncollectedMessages == toSend
        
        where: // run this ten times to give it more of a shake (probably unnecessary and should be removed at some point)
        iteration << [0,1,2,3,4,5,6,7,8,9]
    }

    def cleanup() {
        connection.stop()
    }
}