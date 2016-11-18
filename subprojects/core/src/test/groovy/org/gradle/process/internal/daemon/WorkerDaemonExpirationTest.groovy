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

import org.gradle.process.internal.health.memory.MemoryInfo
import org.gradle.process.internal.health.memory.MemoryAmount
import spock.lang.Specification

class WorkerDaemonExpirationTest extends Specification {

    def workingDir = new File("some-dir")
    def defaultOptions = new DaemonForkOptions(null, null, ['default-options'])
    def oneGbOptions = new DaemonForkOptions('1g', '1g', ['one-gb-options'])
    def twoGbOptions = new DaemonForkOptions('2g', '2g', ['two-gb-options'])
    def threeGbOptions = new DaemonForkOptions('3g', '3g', ['three-gb-options'])
    def memoryInfo = Mock(MemoryInfo) { getTotalPhysicalMemory() >> MemoryAmount.ofGigaBytes(8).bytes }
    def daemonStarter = Mock(WorkerDaemonStarter) {
        startDaemon(_, _, _) >> { Class<? extends WorkerDaemonProtocol> impl, File workDir, DaemonForkOptions forkOptions ->
            Mock(WorkerDaemonClient) {
                getForkOptions() >> forkOptions
                isCompatibleWith(_) >> { DaemonForkOptions otherForkOptions ->
                    forkOptions.isCompatibleWith(otherForkOptions)
                }
            }
        }
    }
    def clientsManager = new WorkerDaemonClientsManager(daemonStarter)
    def expiration = new WorkerDaemonExpiration(clientsManager, memoryInfo)

    def "expires least recently used idle worker daemon to free system memory when requested to release some memory"() {
        given:
        def client1 = reserveNewClient(twoGbOptions)
        def client2 = reserveNewClient(twoGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.attemptToRelease(MemoryAmount.ofGigaBytes(1).bytes)

        then:
        1 * client1.stop()
        0 * client2.stop()

        and:
        reserveIdleClient(twoGbOptions) == client2
    }

    def "expires enough idle worker daemons to release requested memory"() {
        given:
        def client1 = reserveNewClient(threeGbOptions)
        def client2 = reserveNewClient(twoGbOptions)
        def client3 = reserveNewClient(oneGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)
        clientsManager.release(client3)

        when:
        expiration.attemptToRelease(MemoryAmount.ofGigaBytes(4).bytes)

        then:
        1 * client1.stop()
        1 * client2.stop()
        0 * client3.stop()

        and:
        reserveIdleClient(oneGbOptions) == client3
    }

    def "expires all idle daemons when requested memory is equal than what all daemons consume"() {
        given:
        def client1 = reserveNewClient(oneGbOptions)
        def client2 = reserveNewClient(threeGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.attemptToRelease(MemoryAmount.ofGigaBytes(4).bytes)

        then:
        1 * client1.stop()
        1 * client2.stop()

        and:
        reserveIdleClient(twoGbOptions) == null
    }

    def "expires all idle worker daemons when requested memory is larger than what all daemons consume"() {
        given:
        def client1 = reserveNewClient(oneGbOptions)
        def client2 = reserveNewClient(twoGbOptions)
        def client3 = reserveNewClient(threeGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)
        clientsManager.release(client3)

        when:
        expiration.attemptToRelease(MemoryAmount.ofGigaBytes(12).bytes)

        then:
        1 * client1.stop()
        1 * client2.stop()
        1 * client3.stop()

        and:
        reserveIdleClient(twoGbOptions) == null
    }

    def "expires idle worker daemons with default heap size settings"() {
        given:
        def client1 = reserveNewClient(defaultOptions)
        def client2 = reserveNewClient(defaultOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.attemptToRelease(MemoryAmount.ofGigaBytes(1).bytes)

        then:
        1 * client1.stop()
        0 * client2.stop()

        and:
        reserveIdleClient(defaultOptions) == client2
    }

    private WorkerDaemonClient reserveNewClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, forkOptions)
    }

    private WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveIdleClient(forkOptions)
    }
}
