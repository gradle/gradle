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

package org.gradle.process.internal.health.memory

import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.UsesNativeServices
import spock.util.concurrent.PollingConditions

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@UsesNativeServices
class DefaultMemoryManagerTest extends ConcurrentSpec {

    def osMemoryInfo = new TestOsMemoryInfo()
    def jvmMemoryInfo = new DefaultJvmMemoryInfo()
    def conditions = new PollingConditions(timeout: DefaultMemoryManager.STATUS_INTERVAL_SECONDS * 2)
    OsMemoryStatusListener osMemoryStatusListener

    def setup() {
        osMemoryInfo.totalMemory = MemoryAmount.of('8g').bytes
    }

    /**
     * Creates a new MemoryManager suitable for testing.
     * Disable auto free memory request to prevent flakiness.
     * Registers an OS memory status update to initialize the values.
     */
    def newMemoryManager() {
        def listenerManager = Mock(ListenerManager) {
            1 * addListener(_) >> { args -> osMemoryStatusListener = args[0] }
        }
        def memoryManager = new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, Stub(ExecutorFactory), 0.25, false)
        osMemoryStatusListener.onOsMemoryStatus(osMemoryInfo.getOsSnapshot())
        return memoryManager
    }

    def "does not attempt to release memory when claiming 0 memory and free system memory is below threshold"() {
        given:
        osMemoryInfo.freeMemory = MemoryAmount.of('4g').bytes
        def memoryManager = newMemoryManager()

        and:
        def holder = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder)

        when:
        memoryManager.requestFreeMemory(0)

        then:
        0 * holder.attemptToRelease(_)

        cleanup:
        memoryManager.stop()
    }

    def "attempt to release memory when claiming 0 memory and free system memory is above threshold"() {
        given:
        osMemoryInfo.freeMemory =  MemoryAmount.of('1g').bytes
        def memoryManager = newMemoryManager()

        and:
        def holder = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder)

        when:
        memoryManager.requestFreeMemory(0)

        then:
        1 * holder.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes

        cleanup:
        memoryManager.stop()
    }

    def "loop over all memory holders when claiming more memory than releasable"() {
        given:
        osMemoryInfo.freeMemory =  MemoryAmount.of(1).bytes
        def memoryManager = newMemoryManager()

        and:
        def holder1 = Mock(MemoryHolder)
        def holder2 = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder1)
        memoryManager.addMemoryHolder(holder2)

        when:
        memoryManager.requestFreeMemory(MemoryAmount.ofGigaBytes(5).bytes)

        then:
        1 * holder1.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes
        1 * holder2.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes

        cleanup:
        memoryManager.stop()
    }

    def "stop looping over memory holders once claimed memory has been released"() {
        given:
        osMemoryInfo.freeMemory =  MemoryAmount.of('1g').bytes
        def memoryManager = newMemoryManager()

        and:
        def holder1 = Mock(MemoryHolder)
        def holder2 = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder1)
        memoryManager.addMemoryHolder(holder2)

        when:
        memoryManager.requestFreeMemory(MemoryAmount.ofGigaBytes(3).bytes)

        then:
        1 * holder1.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(4).bytes
        0 * holder2.attemptToRelease(_)

        cleanup:
        memoryManager.stop()
    }

    def "only one request for memory is performed for a given snapshot"() {
        given:
        osMemoryInfo.freeMemory =  MemoryAmount.of(1).bytes
        def memoryManager = newMemoryManager()

        and:
        def holder = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder)

        when:
        async {
            start {
                memoryManager.requestFreeMemory(MemoryAmount.ofGigaBytes(3).bytes)
            }
            start {
                memoryManager.requestFreeMemory(MemoryAmount.ofGigaBytes(3).bytes)
            }
        }

        then:
        1 * holder.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(4).bytes

        cleanup:
        memoryManager.stop()
    }

    def "registers/deregisters os memory status listener"() {
        given:
        def listenerManager = Mock(ListenerManager) {
            1 * addListener(_) >> { args -> osMemoryStatusListener = args[0] }
        }
        def memoryManager = new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, new DefaultExecutorFactory(), 0.25, false)

        when:
        memoryManager.stop()

        then:
        1 * listenerManager.removeListener(_) >> { args -> assert args[0] == osMemoryStatusListener }

        cleanup:
        memoryManager.stop()
    }

    def "can add and remove holders concurrently"() {
        given:
        osMemoryInfo.freeMemory =  MemoryAmount.of(1).bytes
        def memoryManager = newMemoryManager()
        def holders = new LinkedBlockingQueue<MemoryHolder>()
        when:
        async {
            10.times {
                start {
                    def holder
                    holder = Mock(MemoryHolder) {
                        attemptToRelease(_) >> {
                            memoryManager.removeMemoryHolder(holders.poll(10, TimeUnit.SECONDS))
                            MemoryAmount.ofGigaBytes(4).bytes
                        }
                    }
                    holders.add(holder)
                    memoryManager.addMemoryHolder(holder)
                }
                start {
                    memoryManager.requestFreeMemory(MemoryAmount.ofGigaBytes(3).bytes)
                }
            }

        }

        then:
        noExceptionThrown()

        cleanup:
        memoryManager.stop()
    }

    def "throwables are not propagated out of the run method of the memory check"() {
        given:
        def check = new DefaultMemoryManager(
            Mock(OsMemoryInfo),
            Mock(JvmMemoryInfo),
            Stub(ListenerManager) {
                getBroadcaster(JvmMemoryStatusListener) >> Mock(JvmMemoryStatusListener)
                getBroadcaster(OsMemoryStatusListener) >> Mock(OsMemoryStatusListener)
            },
            Stub(ExecutorFactory)
        ).new DefaultMemoryManager.MemoryCheck()

        when:
        check.run()

        then:
        1 * _ >> { throw throwable }
        noExceptionThrown()

        where:
        throwable              | _
        new Exception()        | _
        new RuntimeException() | _
        new Error()            | _
    }

    private static class TestOsMemoryInfo implements OsMemoryInfo {
        long totalMemory = 0
        long freeMemory = 0

        @Override
        OsMemoryStatus getOsSnapshot() {
            new OsMemoryStatusSnapshot(totalMemory, freeMemory)
        }
    }
}
