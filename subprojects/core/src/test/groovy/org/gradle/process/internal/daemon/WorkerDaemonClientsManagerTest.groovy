/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon

import org.gradle.util.ConcurrentSpecification
import spock.lang.Subject

class WorkerDaemonClientsManagerTest extends ConcurrentSpecification {

    def workingDir = new File("some-dir")

    def options = Stub(DaemonForkOptions)
    def starter = Stub(WorkerDaemonStarter)
    def serverImpl = Stub(WorkerDaemonProtocol)

    @Subject manager = new WorkerDaemonClientsManager(starter)

    def "does not reserve idle client when no clients"() {
        expect:
        manager.reserveIdleClient(options) == null
    }

    def "does not reserve idle client when no matching client found"() {
        def noMatch = Stub(WorkerDaemonClient) {
            isCompatibleWith(_) >> false
        }

        expect:
        manager.reserveIdleClient(options, [noMatch]) == null
    }

    def "reserves idle client when match found"() {
        def noMatch = Stub(WorkerDaemonClient) { isCompatibleWith(_) >> false }
        def match = Stub(WorkerDaemonClient) { isCompatibleWith(_) >> true }
        def input = [noMatch, match]

        expect:
        manager.reserveIdleClient(options, input) == match
        input == [noMatch] //match removed from input
    }

    def "reserves new client"() {
        def newClient = Stub(WorkerDaemonClient)
        starter.startDaemon(serverImpl.class, workingDir, options) >> newClient

        when:
        def client = manager.reserveNewClient(serverImpl.class, workingDir, options)

        then:
        newClient == client
    }

    def "can stop all created clients"() {
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(serverImpl.class, workingDir, options) >>> [client1, client2]

        when:
        manager.reserveNewClient(serverImpl.class, workingDir, options)
        manager.reserveNewClient(serverImpl.class, workingDir, options)
        manager.stop()

        then:
        1 * client1.stop()
        1 * client2.stop()
    }

    def "clients can be released for further use"() {
        def client = Mock(WorkerDaemonClient) { isCompatibleWith(_) >> true }
        starter.startDaemon(serverImpl.class, workingDir, options) >> client

        when:
        manager.reserveNewClient(serverImpl.class, workingDir, options)

        then:
        manager.reserveIdleClient(options) == null

        when:
        manager.release(client)

        then:
        manager.reserveIdleClient(options) == client
    }
}
