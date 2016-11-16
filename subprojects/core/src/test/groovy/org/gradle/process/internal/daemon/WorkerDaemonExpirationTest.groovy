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

import org.gradle.process.internal.daemon.health.memory.MemoryInfo
import spock.lang.Specification

class WorkerDaemonExpirationTest extends Specification {

    def workingDir = new File("some-dir")
    def oneGbOptions = new DaemonForkOptions('1g', '1g', ['one-gb-options'])
    def twoGbOptions = new DaemonForkOptions('2g', '2g', ['two-gb-options'])
    def threeGbOptions = new DaemonForkOptions('3g', '3g', ['three-gb-options'])
    def memoryInfo = Mock(MemoryInfo) { getTotalPhysicalMemory() >> gb(8) }
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
    def expiration = new WorkerDaemonExpiration(clientsManager, memoryInfo, 0.25)

    def "does not expire worker daemons when enough system memory available"() {
        given:
        def client1 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)
        def client2 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.eventuallyExpireDaemons('1g')

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(3)
        0 * client1.stop()
        0 * client2.stop()

        and:
        clientsManager.reserveIdleClient(twoGbOptions) == client1
    }

    def "expires least recently used idle worker daemon to free system memory when requesting a new one"() {
        given:
        def client1 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)
        def client2 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.eventuallyExpireDaemons('1g')

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * client1.stop()
        0 * client2.stop()

        and:
        clientsManager.reserveIdleClient(twoGbOptions) == client2
    }

    def "expires enough idle worker daemons to fit requested one in system memory"() {
        given:
        def client1 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, threeGbOptions)
        def client2 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)
        def client3 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, oneGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)
        clientsManager.release(client3)

        when:
        expiration.eventuallyExpireDaemons('4g')

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * client1.stop()
        1 * client2.stop()
        0 * client3.stop()

        and:
        clientsManager.reserveIdleClient(oneGbOptions) == client3
    }

    def "expires all idle daemons to fit requested one in system memory"() {
        given:
        def client1 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, oneGbOptions)
        def client2 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, threeGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        expiration.eventuallyExpireDaemons('4g')

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * client1.stop()
        1 * client2.stop()

        and:
        clientsManager.reserveIdleClient(twoGbOptions) == null
    }

    def "expires all idle worker daemons when requested one require more than total system memory"() {
        given:
        def client1 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, oneGbOptions)
        def client2 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, twoGbOptions)
        def client3 = clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, threeGbOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)
        clientsManager.release(client3)

        when:
        expiration.eventuallyExpireDaemons('12g')

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)
        1 * client1.stop()
        1 * client2.stop()
        1 * client3.stop()

        and:
        clientsManager.reserveIdleClient(twoGbOptions) == null
    }

    private WorkerDaemonClient reserveNewClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveNewClient(WorkerDaemonServer, workingDir, forkOptions)
    }

    private WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveIdleClient(forkOptions)
    }

    private static long gb(long gb) {
        gb * 1024 * 1024 * 1024
    }

    private static long mb(long mb) {
        mb * 1024 * 1024
    }
}
