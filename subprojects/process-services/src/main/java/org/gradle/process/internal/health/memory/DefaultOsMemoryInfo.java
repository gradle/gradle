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

import org.gradle.internal.os.OperatingSystem;

public class DefaultOsMemoryInfo implements OsMemoryInfo {

    /**
     * Retrieves the total physical memory size on the system in bytes.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting total physical memory.
     */
    long getTotalPhysicalMemory() {
        return TotalPhysicalMemoryProvider.getTotalPhysicalMemory();
    }

    /**
     * Retrieves the free physical memory on the system in bytes.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting free physical memory.
     */
    long getFreePhysicalMemory() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) {
            return new NativeOsxAvailableMemory().get();
        } else if (operatingSystem.isLinux()) {
            return new MeminfoAvailableMemory().get();
        }
        return new MBeanAvailableMemory().get();
    }

    @Override
    public OsMemoryStatus getOsSnapshot() {
        return new OsMemoryStatusSnapshot(getTotalPhysicalMemory(), getFreePhysicalMemory());
    }
}
