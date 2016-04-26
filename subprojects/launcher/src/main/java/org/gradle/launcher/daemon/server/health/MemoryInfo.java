/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MemoryInfo {
    private final long totalMemory; //this does not change

    public MemoryInfo() {
        totalMemory = Runtime.getRuntime().maxMemory();
    }

    /**
     * Approx. time spent in gc. See {@link GarbageCollectorMXBean}
     */
    public long getCollectionTime() {
        long garbageCollectionTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gc.getCollectionTime();
            if (time >= 0) {
                garbageCollectionTime += time;
            }
        }
        return garbageCollectionTime;
    }

    /**
     * Max memory that this process can commit in bytes.
     * Always returns the same value because maximum memory is determined at jvm start.
     */
    public long getMaxMemory() {
        return totalMemory;
    }

    /**
     * Currently committed memory of this process in bytes.
     * May return different value depending on how the heap has expanded.
     * The returned value is <= {@link #getMaxMemory()}
     */
    public long getCommittedMemory() {
        //querying runtime for each invocation
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Retrieves the total physical memory size on the system in bytes.
     * This value is independent of {@link #getMaxMemory()}, which is the total memory available to the JVM.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting total physical memory.
     */
    public long getTotalPhysicalMemory() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        Throwable rootCause = null;
        try {
            Method getTotalPhysicalMemorySize = bean.getClass().getMethod("getTotalPhysicalMemorySize");
            return (Long) getTotalPhysicalMemorySize.invoke(bean);
        } catch (NoSuchMethodException e) {
            rootCause = e;
        } catch (IllegalAccessException e) {
            rootCause = e;
        } catch (InvocationTargetException e) {
            rootCause = e;
        }
        throw new UnsupportedOperationException("getTotalPhysicalMemory is unsupported on this JVM.", rootCause);
    }

    /**
     * Retrieves the free physical memory on the system in bytes.
     * This value is independent of {@link #getCommittedMemory()}, which is the memory reserved by the JVM.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting free physical memory.
     */
    public long getFreePhysicalMemory() {
        OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        Throwable rootCause = null;
        try {
            Method getFreePhysicalMemorySize = bean.getClass().getMethod("getFreePhysicalMemorySize");
            return (Long) getFreePhysicalMemorySize.invoke(bean);
        } catch (NoSuchMethodException e) {
            rootCause = e;
        } catch (IllegalAccessException e) {
            rootCause = e;
        } catch (InvocationTargetException e) {
            rootCause = e;
        }
        throw new UnsupportedOperationException("getFreePhysicalMemory is unsupported on this JVM.", rootCause);
    }
}
