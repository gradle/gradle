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

import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecHandleFactory;

public class MemoryInfo {

    private final ExecHandleFactory execHandleFactory;
    private final long totalMemory; //this does not change

    public MemoryInfo(ExecHandleFactory execHandleFactory) {
        this.execHandleFactory = execHandleFactory;
        this.totalMemory = Runtime.getRuntime().maxMemory();
    }

    /**
     * Max memory that this process can commit in bytes. Always returns the same value because maximum memory is determined at jvm start.
     */
    public long getMaxMemory() {
        return totalMemory;
    }

    /**
     * Currently committed memory of this process in bytes. May return different value depending on how the heap has expanded. The returned value is <= {@link #getMaxMemory()}
     */
    public long getCommittedMemory() {
        //querying runtime for each invocation
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Retrieves the total physical memory size on the system in bytes. This value is independent of {@link #getMaxMemory()}, which is the total memory available to the JVM.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting total physical memory.
     */
    public long getTotalPhysicalMemory() throws UnsupportedOperationException {
        String attribute = Jvm.current().isIbmJvm() ? "TotalPhysicalMemory" : "TotalPhysicalMemorySize";
        return MBeanAttributeProvider.getMbeanAttribute("java.lang:type=OperatingSystem", attribute, Long.class);
    }

    /**
     * Retrieves the free physical memory on the system in bytes. This value is independent of {@link #getCommittedMemory()}, which is the memory reserved by the JVM.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting free physical memory.
     */
    public long getFreePhysicalMemory() throws UnsupportedOperationException {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) {
            return new VmstatAvailableMemory(execHandleFactory).get();
        } else if (operatingSystem.isLinux()) {
            return new MeminfoAvailableMemory().get();
        }
        return new MBeanAvailableMemory().get();
    }

    public JvmMemoryStatus getJvmSnapshot() {
        return new JvmMemoryStatusSnapshot(getMaxMemory(), getCommittedMemory());
    }

    public OsMemoryStatus getOsSnapshot() throws UnsupportedOperationException {
        return new OsMemoryStatusSnapshot(getTotalPhysicalMemory(), getFreePhysicalMemory());
    }
}
