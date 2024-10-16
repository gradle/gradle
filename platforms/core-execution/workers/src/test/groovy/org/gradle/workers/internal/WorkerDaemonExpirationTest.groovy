/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.health.memory.DefaultMBeanAttributeProvider
import org.gradle.process.internal.health.memory.JvmMemoryStatus
import org.gradle.process.internal.health.memory.MBeanOsMemoryInfo
import org.gradle.process.internal.health.memory.MaximumHeapHelper
import org.gradle.process.internal.health.memory.MemoryAmount
import org.gradle.process.internal.health.memory.MemoryManager
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class WorkerDaemonExpirationTest extends Specification {
    static final int OS_MEMORY_GB = 6

    def defaultOptions = daemonForkOptions(null, null, ['default-options'])
    def oneGbOptions = daemonForkOptions('1g', '1g', ['one-gb-options'])
    def twoGbOptions = daemonForkOptions('2g', '2g', ['two-gb-options'])
    def threeGbOptions = daemonForkOptions('3g', '3g', ['three-gb-options'])
    def reportsMemoryUsage = true
    def daemonStarter = Mock(WorkerDaemonStarter) {
        startDaemon(_) >> { DaemonForkOptions forkOptions ->
            Mock(WorkerDaemonClient) {
                getForkOptions() >> forkOptions
                isCompatibleWith(_) >> { DaemonForkOptions otherForkOptions ->
                    forkOptions.isCompatibleWith(otherForkOptions)
                }
                getJvmMemoryStatus() >> Mock(JvmMemoryStatus) {
                    getCommittedMemory() >> {
                        if (reportsMemoryUsage) {
                            return MemoryAmount.of(forkOptions.jvmOptions.maxHeapSize).bytes
                        } else {
                            throw new IllegalStateException()
                        }
                    }
                }
            }
        }
    }
    def clientsManager = new WorkerDaemonClientsManager(daemonStarter, Mock(ListenerManager), Mock(LoggingManagerInternal), Mock(MemoryManager), new MBeanOsMemoryInfo(new DefaultMBeanAttributeProvider()))
    def expiration = new WorkerDaemonExpiration(clientsManager, MemoryAmount.ofGigaBytes(OS_MEMORY_GB).bytes)

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

    def "expires idle worker daemons workers that have not provided usage but max heap is specified"() {
        given:
        reportsMemoryUsage = false
        def client1 = reserveNewClient(threeGbOptions)
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

    def "expires idle worker daemons workers that have not provided usage and max heap is not specified"() {
        long requestedMemory = Jvm.current().isIbmJvm() ? MemoryAmount.parseNotation("512m") : MemoryAmount.ofGigaBytes(1).bytes

        given:
        reportsMemoryUsage = false
        def client1 = reserveNewClient(defaultOptions)
        def client2 = reserveNewClient(defaultOptions)

        and:
        clientsManager.release(client1)
        clientsManager.release(client2)

        when:
        def released = expiration.attemptToRelease(requestedMemory)

        then:
        1 * client1.stop()
        0 * client2.stop()

        and:
        reserveIdleClient(defaultOptions) == client2

        and:
        released == new MaximumHeapHelper().getDefaultMaximumHeapSize(MemoryAmount.ofGigaBytes(OS_MEMORY_GB).bytes)
    }

    private WorkerDaemonClient reserveNewClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveNewClient(forkOptions)
    }

    private WorkerDaemonClient reserveIdleClient(DaemonForkOptions forkOptions) {
        return clientsManager.reserveIdleClient(forkOptions)
    }

    private JavaForkOptions javaForkOptions(String minHeap, String maxHeap, List<String> jvmArgs) {
        def options = TestFiles.execFactory().newJavaForkOptions()
        options.workingDir = systemSpecificAbsolutePath("foo")
        options.minHeapSize = minHeap
        options.maxHeapSize = maxHeap
        options.jvmArgs = jvmArgs
        return options
    }

    private DaemonForkOptions daemonForkOptions(String minHeap, String maxHeap, List<String> jvmArgs) {
        return new DaemonForkOptionsBuilder(TestFiles.execFactory())
            .javaForkOptions(javaForkOptions(minHeap, maxHeap, jvmArgs))
            .keepAliveMode(KeepAliveMode.SESSION)
            .build()
    }
}
