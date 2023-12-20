/*
 * Copyright 2023 the original author or authors.
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

import net.rubygrapefruit.platform.memory.MemoryInfo
import net.rubygrapefruit.platform.memory.WindowsMemoryInfo
import spock.lang.Specification

class WindowsOSMemoryInfoTest extends Specification {

    def "returns an accurate snapshot from memory info when virtual memory is available"() {
        def info = Mock(WindowsMemoryInfo)

        when:
        def snapshot = WindowsOsMemoryInfo.snapshotFromMemoryInfo(info)

        then:
        1 * info.getTotalPhysicalMemory() >> MemoryAmount.ofGigaBytes(32).bytes
        1 * info.getAvailablePhysicalMemory() >> MemoryAmount.ofGigaBytes(16).bytes
        2 * info.getCommitLimit() >> MemoryAmount.ofGigaBytes(64).bytes
        1 * info.getCommitTotal() >> MemoryAmount.ofGigaBytes(48).bytes

        and:
        snapshot.getPhysicalMemory().getTotal() == MemoryAmount.ofGigaBytes(32).bytes
        snapshot.getPhysicalMemory().getFree() == MemoryAmount.ofGigaBytes(16).bytes
        snapshot.getVirtualMemory() instanceof OsMemoryStatusAspect.Available
        snapshot.getVirtualMemory().getTotal() == MemoryAmount.ofGigaBytes(64).bytes
        snapshot.getVirtualMemory().getFree() == MemoryAmount.ofGigaBytes(16).bytes
    }

    def "returns an accurate snapshot from memory info when virtual memory is NOT available"() {
        def info = Mock(MemoryInfo)

        when:
        def snapshot = WindowsOsMemoryInfo.snapshotFromMemoryInfo(info)

        then:
        1 * info.getTotalPhysicalMemory() >> MemoryAmount.ofGigaBytes(32).bytes
        1 * info.getAvailablePhysicalMemory() >> MemoryAmount.ofGigaBytes(16).bytes

        and:
        snapshot.getPhysicalMemory().getTotal() == MemoryAmount.ofGigaBytes(32).bytes
        snapshot.getPhysicalMemory().getFree() == MemoryAmount.ofGigaBytes(16).bytes
        snapshot.getVirtualMemory() instanceof OsMemoryStatusAspect.Unavailable
    }
}
