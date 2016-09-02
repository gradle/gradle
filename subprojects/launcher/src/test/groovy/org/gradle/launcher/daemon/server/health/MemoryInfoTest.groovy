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
package org.gradle.launcher.daemon.server.health

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class MemoryInfoTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    // We can't exercise both paths at once because we have no control here over the JVM we're running on.
    // However, this will be fully exercised since we test across JVMs.
    def memTest(memCall) {
        try {
            memCall()
            Class<?> sunClass = ClassLoader.getSystemClassLoader().loadClass("com.sun.management.OperatingSystemMXBean")
            return sunClass != null
        } catch (UnsupportedOperationException e) {
            try {
                ClassLoader.getSystemClassLoader().loadClass("com.sun.management.OperatingSystemMXBean")
            } catch (ClassNotFoundException expected) {
                return true
            }
            return false
        }
    }

    def "getTotalPhysicalMemory only throws when memory management methods are unavailable"() {
        expect:
        memTest({ new MemoryInfo().getTotalPhysicalMemory() })
    }

    // We only use MX Bean methods on Windows
    @Requires(TestPrecondition.WINDOWS)
    def "getFreePhysicalMemory only throws when memory management methods are unavailable"() {
        expect:
        memTest({ new MemoryInfo().getFreePhysicalMemory() })
    }

    def "parses free memory from /proc/meminfo on Linux"() {
        expect:
        new MemoryInfo().parseFreeMemoryFromMeminfo(mockMeminfoOutput()) == 2_163_265_536L
    }

    def "parses free memory from vm_stat on MacOS"() {
        expect:
        new MemoryInfo().parseFreeMemoryFromVmstat(mockVmstatOutput()) == 5_717_209_088L
    }

    def "parseFreeMemoryFromMeminfo returns -1 given unparsable file"() {
        expect:
        new MemoryInfo().parseFreeMemoryFromMeminfo(["bogustown"]) == -1L
    }

    private static List<String> mockMeminfoOutput() {
"""MemTotal:       32807232 kB
MemFree:          315332 kB
MemAvailable:    2112564 kB
Buffers:          452252 kB
Cached:          1425068 kB
SwapCached:        80148 kB
Active:          1587152 kB
Inactive:        1029860 kB
Active(anon):     213704 kB
Inactive(anon):   526268 kB
Active(file):    1373448 kB
Inactive(file):   503592 kB
Unevictable:           0 kB
Mlocked:               0 kB
SwapTotal:      16777212 kB
SwapFree:       16550088 kB
Dirty:               104 kB
Writeback:             0 kB
AnonPages:        714372 kB
Mapped:            26076 kB
Shmem:               280 kB
Slab:             209636 kB
SReclaimable:     173608 kB
SUnreclaim:        36028 kB
KernelStack:        6960 kB
PageTables:        23980 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:    18435228 kB
Committed_AS:    2004416 kB
VmallocTotal:   34359738367 kB
VmallocUsed:      186088 kB
VmallocChunk:   34359452624 kB
HardwareCorrupted:     0 kB
AnonHugePages:    194560 kB
CmaTotal:              0 kB
CmaFree:               0 kB
HugePages_Total:   14400
HugePages_Free:       60
HugePages_Rsvd:        0
HugePages_Surp:        0
Hugepagesize:       2048 kB
DirectMap4k:      197056 kB
DirectMap2M:     5953536 kB
DirectMap1G:    28311552 kB
""".split(/\n/)
    }

    private static List<String> mockVmstatOutput() {
"""Mach Virtual Memory Statistics: (page size of 4096 bytes)
Pages free:                              235271.
Pages active:                           1552064.
Pages inactive:                         1419355.
Pages speculative:                        28612.
Pages throttled:                              0.
Pages wired down:                        801506.
Pages purgeable:                          59751.
"Translation faults":                 710260691.
Pages copy-on-write:                   27561109.
Pages zero filled:                    244159969.
Pages reactivated:                      3774906.
Pages purged:                            420232.
File-backed pages:                      1160532.
Anonymous pages:                        1839499.
Pages stored in compressor:              422034.
Pages occupied by compressor:            156894.
Decompressions:                         3406193.
Compressions:                           5099722.
Pageins:                                4299406.
Pageouts:                                 41419.
Swapins:                                2439008.
Swapouts:                               2552292.
""".split(/\n/)
    }
}
