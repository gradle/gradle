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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.gradle.internal.Cast;
import org.gradle.internal.os.OperatingSystem;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MemoryInfo {

    private final long totalMemory; //this does not change
    private final Matcher meminfoMatcher;
    private final Matcher vmstatMatcher;

    // /proc/meminfo is in kB since Linux 4.0, see https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/tree/fs/proc/task_mmu.c?id=39a8804455fb23f09157341d3ba7db6d7ae6ee76#n22
    private static final Pattern MEMINFO_LINE_PATTERN = Pattern.compile("^\\D+(\\d+) kB$");
    private static final Pattern VMSTAT_LINE_PATTERN = Pattern.compile("^\\D+(\\d+)\\D+$");
    private static final String MEMINFO_EXECUTABLE_PATH = "/proc/meminfo";
    private static final String VMSTAT_EXECUTABLE_PATH = "/usr/bin/vm_stat";

    MemoryInfo() {
        totalMemory = Runtime.getRuntime().maxMemory();

        // Initialize Matchers once and then reset them for performance
        meminfoMatcher = MEMINFO_LINE_PATTERN.matcher("");
        vmstatMatcher = VMSTAT_LINE_PATTERN.matcher("");
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
    public long getTotalPhysicalMemory() {
        return getMbeanAttribute("java.lang:type=OperatingSystem", "TotalPhysicalMemorySize", Long.class);
    }

    /**
     * Retrieves the free physical memory on the system in bytes. This value is independent of {@link #getCommittedMemory()}, which is the memory reserved by the JVM.
     *
     * @throws UnsupportedOperationException if the JVM doesn't support getting free physical memory.
     */
    public long getFreePhysicalMemory() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) { // Parse /usr/bin/vm_stat output
            List<String> vmstatOutputLines = exec(VMSTAT_EXECUTABLE_PATH);
            long freeMemoryFromVmstat = parseFreeMemoryFromVmstat(vmstatOutputLines);
            if (freeMemoryFromVmstat != -1) {
                return freeMemoryFromVmstat;
            }
        } else if (operatingSystem.isLinux()) { // Parse /proc/meminfo output
            List<String> meminfoOutputLines = exec(MEMINFO_EXECUTABLE_PATH);
            long freeMemoryFromProcMeminfo = parseFreeMemoryFromMeminfo(meminfoOutputLines);
            if (freeMemoryFromProcMeminfo != -1) {
                return freeMemoryFromProcMeminfo;
            }
        } else {
            // MBean value takes reclaimable memory into account on Windows and Solaris
            // See https://msdn.microsoft.com/en-us/library/windows/desktop/aa366770(v=vs.85).aspx
            // See https://github.com/dmlloyd/openjdk/blob/jdk8u/jdk8u/jdk/src/solaris/native/sun/management/OperatingSystemImpl.c#L341
            return getMbeanAttribute("java.lang:type=OperatingSystem", "FreePhysicalMemorySize", Long.class);
        }

        // Couldn't get free memory
        throw new UnsupportedOperationException("Unable to get free memory");
    }

    /**
     * Execute given executable and return standard out as a list of strings.
     */
    private List<String> exec(final String executablePath) {
        List<String> vmstatOutputLines = Lists.newArrayList();
        ProcessBuilder processBuilder = new ProcessBuilder(executablePath);
        try {
            Process process = processBuilder.start();
            if (process.waitFor() == 0) {
                BufferedReader is = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = is.readLine()) != null) {
                    vmstatOutputLines.add(line);
                }
            }
        } catch (IOException e) {
            throw new UnsupportedOperationException("Unable to get free memory from " + executablePath, e);
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException("Unable to get free memory from " + executablePath, e);
        }
        return vmstatOutputLines;
    }

    /**
     * Given output from /proc/meminfo, return available memory in bytes.
     */
    @VisibleForTesting
    long parseFreeMemoryFromMeminfo(final List<String> meminfoLines) {
        final AvailableMemory availableMemory = new AvailableMemory();

        for (String line : meminfoLines) {
            if (line.startsWith("MemAvailable")) {
                return parseMeminfoBytes(line);
            } else if (line.startsWith("MemFree")) {
                availableMemory.setFreeBytes(parseMeminfoBytes(line));
            } else if (line.startsWith("Cached")) {
                availableMemory.setReclaimableBytes(parseMeminfoBytes(line));
            }
        }

        return availableMemory.getAvailableBytes();
    }

    /**
     * Given a line from /proc/meminfo, return number value representing number of bytes.
     *
     * @param line String line from /proc/meminfo. Example: "MemAvailable:    2109560 kB"
     * @return number from value transformed to bytes or -1 if unparsable. Example: 2_160_189_440
     */
    private long parseMeminfoBytes(final String line) {
        Matcher matcher = meminfoMatcher.reset(line);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1)) * 1024;
        }
        throw new UnsupportedOperationException("Unable to parse /proc/meminfo output to get available memory");
    }

    /**
     * Given a file referencing /usr/bin/vm_stat, return available memory in bytes.
     */
    @VisibleForTesting
    long parseFreeMemoryFromVmstat(final List<String> vmstatLines) {
        final AvailableMemory availableMemory = new AvailableMemory();

        if (!vmstatLines.isEmpty()) {
            long pageSize = parseVmstatBytes(vmstatLines.get(0));
            for (String line : vmstatLines) {
                if (line.startsWith("Pages free")) {
                    availableMemory.setFreeBytes(parseVmstatBytes(line) * pageSize);
                } else if (line.startsWith("File-backed pages")) {
                    availableMemory.setReclaimableBytes(parseVmstatBytes(line) * pageSize);
                }
            }
        }

        return availableMemory.getAvailableBytes();
    }

    private long parseVmstatBytes(final String line) {
        Matcher matcher = vmstatMatcher.reset(line);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new UnsupportedOperationException("Unable to parse vm_stat output to get available memory");
    }

    /**
     * Calls an mbean method if available.
     *
     * @throws UnsupportedOperationException if this method isn't available on this JVM.
     */
    private static <T> T getMbeanAttribute(String mbean, final String attribute, Class<T> type) {
        Exception rootCause;
        try {
            ObjectName objectName = new ObjectName(mbean);
            return Cast.cast(type, ManagementFactory.getPlatformMBeanServer().getAttribute(objectName, attribute));
        } catch (InstanceNotFoundException e) {
            rootCause = e;
        } catch (ReflectionException e) {
            rootCause = e;
        } catch (MalformedObjectNameException e) {
            rootCause = e;
        } catch (MBeanException e) {
            rootCause = e;
        } catch (AttributeNotFoundException e) {
            rootCause = e;
        }
        throw new UnsupportedOperationException("(" + mbean + ")." + attribute + " is unsupported on this JVM.", rootCause);
    }

    private class AvailableMemory {
        private long freeBytes = -1;
        private long reclaimableBytes = -1;

        public void setFreeBytes(long freeBytes) {
            this.freeBytes = freeBytes;
        }

        public void setReclaimableBytes(long reclaimableBytes) {
            this.reclaimableBytes = reclaimableBytes;
        }

        public long getAvailableBytes() {
            if (freeBytes != -1 && reclaimableBytes != -1) {
                return freeBytes + reclaimableBytes;
            }
            return -1;
        }
    }
}
