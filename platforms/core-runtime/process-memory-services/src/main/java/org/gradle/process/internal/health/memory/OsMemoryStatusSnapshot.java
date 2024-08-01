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

public class OsMemoryStatusSnapshot implements OsMemoryStatus {
    private final OsMemoryStatusAspect.Available physicalMemory;
    private final OsMemoryStatusAspect virtualMemory;

    /**
     * Create a new snapshot with unknown virtual memory.
     *
     * @param totalPhysicalMemory total physical memory in bytes
     * @param freePhysicalMemory free physical memory in bytes
     */
    public OsMemoryStatusSnapshot(long totalPhysicalMemory, long freePhysicalMemory) {
        this(
            new DefaultAvailableOsMemoryStatusAspect("physical", totalPhysicalMemory, freePhysicalMemory),
            new DefaultUnavailableOsMemoryStatusAspect("virtual")
        );
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
        this(
            new DefaultAvailableOsMemoryStatusAspect("physical", totalPhysicalMemory, freePhysicalMemory),
            new DefaultAvailableOsMemoryStatusAspect("virtual", totalVirtualMemory, freeVirtualMemory)
        );
    }

    private OsMemoryStatusSnapshot(OsMemoryStatusAspect.Available physicalMemory, OsMemoryStatusAspect virtualMemory) {
        this.physicalMemory = physicalMemory;
        this.virtualMemory = virtualMemory;
    }

    @Override
    public OsMemoryStatusAspect.Available getPhysicalMemory() {
        return physicalMemory;
    }

    @Override
    public OsMemoryStatusAspect getVirtualMemory() {
        return virtualMemory;
    }

    @Override
    public String toString() {
        return "OS memory {" + physicalMemory + ", " + virtualMemory + '}';
    }
}
