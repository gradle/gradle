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

package org.gradle.process.internal.health.memory;

import com.google.common.base.Function;

public class OsMemoryStatusSnapshot implements OsMemoryStatus {
    private static final long UNAVAILABLE = -1;

    private final long totalPhysicalMemory;
    private final long freePhysicalMemory;
    private final long totalVirtualMemory;
    private final long freeVirtualMemory;

    /**
     * Create a new snapshot with unavailable virtual memory information.
     *
     * @param totalPhysicalMemory total physical memory in bytes
     * @param freePhysicalMemory free physical memory in bytes
     */
    public OsMemoryStatusSnapshot(long totalPhysicalMemory, long freePhysicalMemory) {
        if (totalPhysicalMemory < 0) {
            throw new IllegalArgumentException("totalPhysicalMemory must be >= 0");
        }
        if (freePhysicalMemory < 0) {
            throw new IllegalArgumentException("freePhysicalMemory must be >= 0");
        }
        this.totalPhysicalMemory = totalPhysicalMemory;
        this.freePhysicalMemory = freePhysicalMemory;
        this.totalVirtualMemory = UNAVAILABLE;
        this.freeVirtualMemory = UNAVAILABLE;
    }

    /**
     * Create a new snapshot with limited virtual memory.
     *
     * @param totalPhysicalMemory total physical memory in bytes
     * @param freePhysicalMemory free physical memory in bytes
     * @param totalVirtualMemory total virtual memory in bytes
     * @param freeVirtualMemory free virtual memory in bytes
     */
    public OsMemoryStatusSnapshot(
        long totalPhysicalMemory, long freePhysicalMemory, long totalVirtualMemory, long freeVirtualMemory
    ) {
        if (totalPhysicalMemory < 0) {
            throw new IllegalArgumentException("totalPhysicalMemory must be >= 0");
        }
        if (freePhysicalMemory < 0) {
            throw new IllegalArgumentException("freePhysicalMemory must be >= 0");
        }
        if (totalVirtualMemory < 0) {
            throw new IllegalArgumentException("totalVirtualMemory must be >= 0");
        }
        if (freeVirtualMemory < 0) {
            throw new IllegalArgumentException("freeVirtualMemory must be >= 0");
        }
        this.totalPhysicalMemory = totalPhysicalMemory;
        this.freePhysicalMemory = freePhysicalMemory;
        this.totalVirtualMemory = totalVirtualMemory;
        this.freeVirtualMemory = freeVirtualMemory;
    }

    @Override
    public long getTotalPhysicalMemory() {
        return totalPhysicalMemory;
    }

    // exposed for DefaultOsMemoryInfo
    /**
     * Get the total physical memory in bytes.
     *
     * @return total physical memory in bytes
     */
    public long getFreePhysicalMemory() {
        return freePhysicalMemory;
    }

    @Override
    public MemoryReclaim computeMemoryReclaimAmount(Function<Long, Long> computeIntendedFreeMemory) {
        MemoryReclaim physicalMemoryReclaim = computeSpecificMemoryReclaimAmount(
            "physical", totalPhysicalMemory, freePhysicalMemory, computeIntendedFreeMemory
        );
        MemoryReclaim virtualMemoryReclaim = computeSpecificMemoryReclaimAmount(
            "virtual", totalVirtualMemory, freeVirtualMemory, computeIntendedFreeMemory
        );
        return physicalMemoryReclaim.merge(virtualMemoryReclaim);
    }

    private static MemoryReclaim computeSpecificMemoryReclaimAmount(
        String type, long totalMemory, long currentFree, Function<Long, Long> computeIntendedFreeMemory
    ) {
        if (totalMemory == UNAVAILABLE || currentFree == UNAVAILABLE) {
            return MemoryReclaim.none();
        }
        long requiredFree = computeIntendedFreeMemory.apply(totalMemory);
        if (requiredFree < 0) {
            throw new IllegalArgumentException("computeIntendedFreeMemory must return a value >= 0");
        }
        if (currentFree >= requiredFree) {
            return MemoryReclaim.none();
        }
        return MemoryReclaim.some(type, currentFree, requiredFree);
    }

    @Override
    public String toString() {
        return "OS memory {"
            + "totalPhysicalMemory=" + totalPhysicalMemory + ", "
            + "freePhysicalMemory=" + freePhysicalMemory + ", "
            + "totalVirtualMemory=" + totalVirtualMemory + ", "
            + "freeVirtualMemory=" + freeVirtualMemory
            + "}";
    }
}
