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

import spock.lang.Specification

class MemInfoOsMemoryInfoTest extends Specification {
    def "parses memory from /proc/meminfo on Linux 3.x"() {
        given:
        def snapshot = new MemInfoOsMemoryInfo().getOsSnapshotFromMemInfo(meminfoLinux3())

        expect:
        snapshot.physicalMemory.free == 32_343_658_496L
        snapshot.physicalMemory.total == 50_650_296_320L
        snapshot.virtualMemory instanceof OsMemoryCategory.Unknown
    }

    def "parses memory from /proc/meminfo on Linux 4.x"() {
        given:
        def snapshot = new MemInfoOsMemoryInfo().getOsSnapshotFromMemInfo(meminfoLinux4())

        expect:
        snapshot.physicalMemory.free == 2_163_265_536L
        snapshot.physicalMemory.total == 33_594_605_568L
        snapshot.virtualMemory instanceof OsMemoryCategory.Unknown
    }

    def "throws unsupported operation exception when non-numeric values are provided"() {
        when:
        new MemInfoOsMemoryInfo().getOsSnapshotFromMemInfo(meminfo)

        then:
        thrown(UnsupportedOperationException)

        where:
        meminfo << [["bogustown"], bogusMeminfoLinux3(), bogusMeminfoLinux4()]
    }

    private static List<String> meminfoLinux3() {
        """MemTotal:       49463180 kB
MemFree:        15953088 kB
Buffers:          267852 kB
Cached:         14152476 kB
SwapCached:            0 kB
Active:         17791108 kB
Inactive:       13818288 kB
Active(anon):   17189920 kB
Inactive(anon):      460 kB
Active(file):     601188 kB
Inactive(file): 13817828 kB
Unevictable:           0 kB
Mlocked:               0 kB
SwapTotal:             0 kB
SwapFree:              0 kB
Dirty:                20 kB
Writeback:             0 kB
AnonPages:      17189100 kB
Mapped:            24008 kB
Shmem:              1312 kB
Slab:            1291916 kB
SReclaimable:    1236196 kB
SUnreclaim:        55720 kB
KernelStack:        2888 kB
PageTables:        41200 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:    24731588 kB
Committed_AS:   22081020 kB
VmallocTotal:   34359738367 kB
VmallocUsed:      173104 kB
VmallocChunk:   34359438128 kB
HardwareCorrupted:     0 kB
AnonHugePages:  17104896 kB
HugePages_Total:       0
HugePages_Free:        0
HugePages_Rsvd:        0
HugePages_Surp:        0
Hugepagesize:       2048 kB
DirectMap4k:      107840 kB
DirectMap2M:    50223104 kB
""".split(/\n/)
    }

    private static List<String> meminfoLinux4() {
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

    private static List<String> bogusMeminfoLinux3() {
        """MemTotal:       foo
MemFree:        bar
Buffers:          baz
Cached:         14152476 kB
SwapCached:            0 kB
Active:         17791108 kB
Inactive:       13818288 kB
Active(anon):   17189920 kB
Inactive(anon):      460 kB
Active(file):     601188 kB
Inactive(file): 13817828 kB
Unevictable:           0 kB
Mlocked:               0 kB
SwapTotal:             0 kB
SwapFree:              0 kB
Dirty:                20 kB
Writeback:             0 kB
AnonPages:      17189100 kB
Mapped:            24008 kB
Shmem:              1312 kB
Slab:            1291916 kB
SReclaimable:    1236196 kB
SUnreclaim:        55720 kB
KernelStack:        2888 kB
PageTables:        41200 kB
NFS_Unstable:          0 kB
Bounce:                0 kB
WritebackTmp:          0 kB
CommitLimit:    24731588 kB
Committed_AS:   22081020 kB
VmallocTotal:   34359738367 kB
VmallocUsed:      173104 kB
VmallocChunk:   34359438128 kB
HardwareCorrupted:     0 kB
AnonHugePages:  17104896 kB
HugePages_Total:       0
HugePages_Free:        0
HugePages_Rsvd:        0
HugePages_Surp:        0
Hugepagesize:       2048 kB
DirectMap4k:      107840 kB
DirectMap2M:    50223104 kB
""".split(/\n/)
    }

    private static List<String> bogusMeminfoLinux4() {
        """MemTotal:       foo
MemFree:          315332 kB
MemAvailable:    bar
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
}
