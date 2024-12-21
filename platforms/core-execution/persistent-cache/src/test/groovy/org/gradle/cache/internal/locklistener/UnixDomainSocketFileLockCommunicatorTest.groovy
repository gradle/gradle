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


package org.gradle.cache.internal.locklistener


import org.gradle.util.ConcurrentSpecification

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class UnixDomainSocketFileLockCommunicatorTest extends ConcurrentSpecification {

    String pid = UnixDomainSocketFileLockCommunicator.currentPid as String
    def communicator = new UnixDomainSocketFileLockCommunicator()

    def cleanup() {
        communicator.stop()
    }

    def "knows port"() {
        expect:
        communicator.getPort() != -1
    }

    def "does not know port after stopping"() {
        when:
        communicator.stop()

        then:
        communicator.getPort() == -1
    }

    def "can receive lock id and type"() {
        FileLockPacketPayload receivedPayload

        start {
            def packet = communicator.receive()
            receivedPayload = communicator.decode(packet)
        }

        poll {
            assert communicator.getPort() != -1 && receivedPayload == null
        }

        when:
        Thread.sleep(1000)
        communicator.pingOwner(pid, communicator.getPort(), 155, "lock")

        then:
        poll {
            assert receivedPayload.lockId == 155
            assert receivedPayload.type == FileLockPacketType.UNLOCK_REQUEST
        }
    }

    def "can receive lock id despite missing type"() {
        FileLockPacketPayload receivedPayload

        start {
            def packet = communicator.receive()
            receivedPayload = communicator.decode(packet)
        }

        poll {
            assert communicator.getPort() != -1 && receivedPayload == null
        }

        when:
        def socket = communicator.unixDomainSocketAddressOf(pid, communicator.getPort())
        // Payload: [pid, inProcessId, <data>]
        def bytes = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 155] as byte[]
        try (SocketChannel clientChannel = SocketChannel.open(socket)) {
            clientChannel.write(ByteBuffer.wrap(bytes));
        }

        then:
        poll {
            assert receivedPayload.lockId == 155
            assert receivedPayload.type == FileLockPacketType.UNKNOWN
        }
    }

    def "may not receive after the stop"() {
        communicator.stop()
        when:
        communicator.receive()
        then:
        thrown(GracefullyStoppedException)
    }

    def "pinging on a port that nobody listens is safe"() {
        when:
        communicator.pingOwner(pid, 6666, 166, "lock")

        then:
        noExceptionThrown()
    }

    def "can be stopped"() {
        expect:
        communicator.stop()
    }

    def "can be stopped during receive"() {
        start {
            try {
                communicator.receive()
            } catch (GracefullyStoppedException e) {}
        }

        when:
        communicator.stop()

        then:
        finished()
    }
}
