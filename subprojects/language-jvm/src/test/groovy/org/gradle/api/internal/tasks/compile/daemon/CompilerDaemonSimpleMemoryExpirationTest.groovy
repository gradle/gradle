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

package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.process.internal.daemon.health.memory.MemoryInfo
import spock.lang.Specification
import spock.lang.Unroll

class CompilerDaemonSimpleMemoryExpirationTest extends Specification {

    def workingDir = new File("some-dir")
    def twoGbOptions = Stub(DaemonForkOptions) { getMaxHeapSize() >> '2g' }
    def fourGbOptions = Stub(DaemonForkOptions) { getMaxHeapSize() >> '4g' }
    def twelveGbOptions = Stub(DaemonForkOptions) { getMaxHeapSize() >> '12g' }
    def memoryInfo = Mock(MemoryInfo) { getTotalPhysicalMemory() >> gb(8) }
    def expiration = new CompilerDaemonSimpleMemoryExpiration(memoryInfo, 0.05)

    def "does not expire worker daemons when enough system memory available"() {
        given:
        def client1 = Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        def client2 = Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        def allClients = [client1, client2]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twoGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(3)

        and:
        idleClients == [client1, client2]
        allClients == [client1, client2]
    }

    def "expires least recently used idle worker daemon to free system memory when requesting a new one"() {
        given:
        def client2 = Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        def allClients = [
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            client2
        ]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twoGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(2)

        and:
        idleClients == [client2]
        allClients == [client2]
    }

    def "expires enough idle worker daemons to fit requested one in system memory"() {
        given:
        def client3 = Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        def allClients = [
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            client3
        ]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(fourGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)

        then:
        idleClients == [client3]
        allClients == [client3]
    }

    def "expires all idle daemons to fit requested one in system memory"() {
        given:
        def allClients = [
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        ]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(fourGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)

        then:
        idleClients == []
        allClients == []
    }

    def "expires all idle daemons when requested one require more than total system memory"() {
        def allClients = [
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions },
            Mock(CompilerDaemonClient) { getForkOptions() >> twoGbOptions }
        ]
        def idleClients = allClients.collect()

        when:
        expiration.eventuallyExpireDaemons(twelveGbOptions, idleClients, allClients)

        then:
        1 * memoryInfo.getFreePhysicalMemory() >> gb(1)

        then:
        idleClients == []
        allClients == []
    }

    def "can parse maximum heap null"() {
        expect:
        CompilerDaemonSimpleMemoryExpiration.parseHeapSize(memoryInfo, null) == CompilerDaemonSimpleMemoryExpiration.getDefaultMaxHeap(memoryInfo)
    }

    @Unroll
    def "can parse maximum heap '#maxHeap'"() {
        given:
        CompilerDaemonSimpleMemoryExpiration.parseHeapSize(memoryInfo, maxHeap) == maxHeapBytes

        where:
        maxHeap | maxHeapBytes
        ''      | CompilerDaemonSimpleMemoryExpiration.getDefaultMaxHeap(Stub(MemoryInfo))
        '512m'  | mb(512)
        '768M'  | mb(768)
        '64g'   | gb(64)
        '4G'    | gb(4)
    }

    private static long gb(long gb) {
        gb * 1024 * 1024 * 1024
    }

    private static long mb(long mb) {
        mb * 1024 * 1024
    }
}
