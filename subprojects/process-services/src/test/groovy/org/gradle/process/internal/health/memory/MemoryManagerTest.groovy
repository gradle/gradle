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

import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.process.internal.DefaultExecActionFactory
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@UsesNativeServices
class MemoryManagerTest extends Specification {

    def memoryInfo = Spy(MemoryInfo, constructorArgs: [new DefaultExecActionFactory(new IdentityFileResolver())])
    def conditions = new PollingConditions(timeout: DefaultMemoryManager.STATUS_INTERVAL_SECONDS * 2)

    def setup() {
        memoryInfo.getTotalPhysicalMemory() >> MemoryAmount.of('8g').bytes
    }

    /**
     * Creates a new MemoryManager suitable for testing.
     * Disable auto free memory request to prevent flakiness.
     * Wait of the first OsMemoryStatus event then stop sampling.
     */
    def newMemoryManager() {
        def memoryManager = new DefaultMemoryManager(memoryInfo, new DefaultListenerManager(), new DefaultExecutorFactory(), 0.25, false)
        conditions.eventually {
            assert memoryManager.osMemoryStatus != null
        }
        memoryManager.stop()
        return memoryManager
    }

    def "does not attempt to release memory when claiming 0 memory and free system memory is below threshold"() {
        given:
        memoryInfo.getFreePhysicalMemory() >> MemoryAmount.of('4g').bytes
        def memoryManager = newMemoryManager()

        and:
        def holder = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder)

        when:
        memoryManager.requestFreeMemory(0)

        then:
        0 * holder.attemptToRelease(_)
    }

    def "attempt to release memory when claiming 0 memory and free system memory is above threshold"() {
        given:
        memoryInfo.getFreePhysicalMemory() >> MemoryAmount.of('1g').bytes
        def memoryManager = newMemoryManager()

        and:
        def holder = Mock(MemoryHolder)
        memoryManager.addMemoryHolder(holder)

        when:
        memoryManager.requestFreeMemory(0)

        then:
        1 * holder.attemptToRelease(_) >> MemoryAmount.ofGigaBytes(2).bytes
    }

    def "loop over all memory holders when claiming more memory than releasable"() {
        given:
        memoryInfo.getFreePhysicalMemory() >> MemoryAmount.of(1).bytes
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
    }

    def "stop looping over memory holders once claimed memory has been released"() {
        given:
        memoryInfo.getFreePhysicalMemory() >> MemoryAmount.of('1g').bytes
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
    }
}
