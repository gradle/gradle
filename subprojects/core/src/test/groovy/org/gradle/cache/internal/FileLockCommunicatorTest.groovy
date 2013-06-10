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


package org.gradle.cache.internal

import org.gradle.util.ConcurrentSpecification

import static org.gradle.cache.internal.FileLockCommunicator.pingOwner
import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

/**
 * By Szczepan Faber on 5/23/13
 */
class FileLockCommunicatorTest extends ConcurrentSpecification {
    //TODO SF review and tighten

    def communicator = new FileLockCommunicator()
    Long receivedId
    long actualId = 123

    def cleanup() {
        if (communicator.started) {
            communicator.stop()
        }
    }

    def "port after starting"() {
        when:
        communicator.start()

        then:
        communicator.getPort() != -1
    }

    def "port after stopping"() {
        when:
        communicator.start()
        communicator.stop()

        then:
        communicator.getPort() == -1
    }

    def "can receive lock id"() {
        start {
            communicator.start()
            receivedId = communicator.receive()
        }

        poll {
            assert communicator.getPort() != -1 && receivedId == null
        }

        when:
        pingOwner(communicator.getPort(), 155)

        then:
        poll {
            assert receivedId == 155
        }
    }

    def "pinging on a port that nobody listens is safe"() {
        when:
        pingOwner(6666, 166)

        then:
        noExceptionThrown()
    }

    def "can be stopped"() {
        start {
            communicator.start()
            try {
                communicator.receive()
            } catch (GracefullyStoppedException e) {}
        }

        sleep(300)

        when:
        communicator.stop()

        then:
        finished()
    }
}
