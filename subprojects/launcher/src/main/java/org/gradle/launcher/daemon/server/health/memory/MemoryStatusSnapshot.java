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

package org.gradle.launcher.daemon.server.health.memory;

public class MemoryStatusSnapshot implements MemoryStatus {
    private final long maxMemory;
    private final long committedMemory;
    private final long totalPhysicalMemory;
    private final long freePhysicalMemory;

    public MemoryStatusSnapshot(long maxMemory, long committedMemory, long totalPhysicalMemory, long freePhysicalMemory) {
        this.maxMemory = maxMemory;
        this.committedMemory = committedMemory;
        this.totalPhysicalMemory = totalPhysicalMemory;
        this.freePhysicalMemory = freePhysicalMemory;
    }

    @Override
    public long getMaxMemory() {
        return maxMemory;
    }

    @Override
    public long getCommittedMemory() {
        return committedMemory;
    }

    @Override
    public long getTotalPhysicalMemory() {
        return totalPhysicalMemory;
    }

    @Override
    public long getFreePhysicalMemory() {
        return freePhysicalMemory;
    }

    @Override
    public String toString() {
        return "{ Max: " + maxMemory + ", Committed: " + committedMemory + ", TotalPhysical: " + totalPhysicalMemory + " FreePhysical: " + freePhysicalMemory + " }";
    }
}
