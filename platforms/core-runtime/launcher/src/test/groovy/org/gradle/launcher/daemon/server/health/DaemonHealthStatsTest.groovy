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

package org.gradle.launcher.daemon.server.health


import org.gradle.launcher.daemon.server.health.gc.DefaultGarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import spock.lang.Specification

class DaemonHealthStatsTest extends Specification {

    def gcInfo = Stub(GarbageCollectionInfo)
    def gcMonitor = Stub(DefaultGarbageCollectionMonitor)
    def runningStats = Stub(DaemonRunningStats)
    def healthStats = new DaemonHealthStats(runningStats, gcInfo, gcMonitor)

    def "includes garbage collection data when present"() {
        when:
        gcInfo.getCollectionTime() >> 25
        gcMonitor.getHeapStats() >> new GarbageCollectionStats(1.0, 103, 1024, 5)
        gcMonitor.getNonHeapStats() >> new GarbageCollectionStats(0, 1024, 2048, 5)
        runningStats.getPrettyUpTime() >> "3 mins"
        runningStats.getAllBuildsTime() >> 1000

        then:
        healthStats.healthInfo == "[uptime: 3 mins, performance: 98%, GC rate: 1.00/s, heap usage: 10% of 1 KiB, non-heap usage: 50% of 2 KiB]"
    }

    def "handles no garbage collection data"() {
        when:
        gcInfo.getCollectionTime() >> 25
        runningStats.getPrettyUpTime() >> "3 mins"
        runningStats.getAllBuildsTime() >> 1000

        gcMonitor.getHeapStats() >> GarbageCollectionStats.noData()
        gcMonitor.getNonHeapStats() >> GarbageCollectionStats.noData()

        then:
        healthStats.healthInfo == "[uptime: 3 mins, performance: 98%]"
    }

}
