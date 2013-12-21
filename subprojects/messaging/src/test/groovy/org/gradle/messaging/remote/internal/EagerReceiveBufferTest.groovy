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

import org.gradle.messaging.dispatch.Receive
import org.gradle.internal.concurrent.DefaultExecutorFactory

import spock.lang.*

class EagerReceiveBufferTest extends Specification {

    def bufferSize = null
    def receivers = []
    def buffer

    def receiver(Object... messages) {
        def list = messages as LinkedList
        receiver { list.poll() }
    }

    def receiver(Closure receiveImpl) {
        receivers << (receiveImpl as Receive)
    }

    def executor() {
        new DefaultExecutorFactory().create("test")
    }

    void bufferSize(int bufferSize) {
        this.bufferSize = bufferSize
    }

    def buffer(Receive... receivers) {
        if (bufferSize == null) {
            new EagerReceiveBuffer(executor(), receivers as List)
        } else {
            new EagerReceiveBuffer(executor(), bufferSize, receivers as List)
        }
    }

    def receive() {
        if (buffer == null) {
            buffer = buffer(*receivers)
            buffer.start()
        }

        buffer.receive()
    }

    def "messages are consumed in order"() {
        when:
        receiver 1, 2, 3

        then:
        receive() == 1
        receive() == 2
        receive() == 3
        receive() == null
        receive() == null
    }

    def "messages are consumed from all receivers"() {
        when:
        receiver 1,2,3
        receiver 4,5,6

        then:
        def messages = (1..6).collect { receive() }
        def grouped = messages.groupBy { it < 4 ? "first" : "second" }
        grouped.first == [1,2,3]
        grouped.second == [4,5,6]
    }

    def "consumption blocks while the buffer is full"() {
        given:
        bufferSize 1

        when:
        def messages = new LinkedList([1,2,3,4])
        receiver { messages.poll() }

        then:
        receive() == 1 // triggers consumption
        sleep 1000 // enough time for the consumer thread to receive from our receiver, and block waiting for free buffer space
        messages == [4] // 2 is on the buffer, 3 is being held waiting for space, 4 hasn't been received yet
        receive() == 2
        sleep 1000 // enough time for 3 to be put on the queue, and 4 to be received and held waiting for space
        messages.empty
        receive() == 3
        receive() == 4
    }

    def "filling the buffer doesn't cause problems"() {
        given:
        bufferSize 1

        when:
        receiver 1,2,3
        receiver 4,5,6
        receiver 7,8,9

        then:
        9.times { assert receive() in 1..9; sleep 100 }
    }

    def "messages held while waiting for buffer space are discarded when stopped"() {
        given:
        bufferSize 1

        when:
        receiver 1,2,3,4

        then:
        receive() == 1
        sleep 1000
        buffer.stop()
        receive() == 2
        receive() == 3
        receive() == null
    }

}