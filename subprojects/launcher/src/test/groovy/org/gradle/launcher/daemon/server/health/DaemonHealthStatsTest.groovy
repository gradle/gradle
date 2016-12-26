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

import org.gradle.internal.event.DefaultListenerManager
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import spock.lang.Specification

class DaemonHealthStatsTest extends Specification {

    def listenerManager = new DefaultListenerManager()
    def gcInfo = Stub(GarbageCollectionInfo)
    def gcMonitor = Stub(GarbageCollectionMonitor)
    def runningStats = Stub(DaemonRunningStats)
    def healthStats = new DaemonHealthStats(runningStats, gcInfo, gcMonitor)

    def "consumes first build"() {
        when:
        runningStats.getBuildCount() >> 0

        then:
        healthStats.healthInfo ==~ /Starting build in new daemon \[memory: [0-9].*/
    }

    def "consumes subsequent builds"() {
        when:
        gcInfo.getCollectionTime() >> 25
        gcMonitor.getTenuredStats() >> {
            Stub(GarbageCollectionStats) {
                getUsage() >> 10
                getMax() >> 1024
                getRate() >> 1.0
            }
        }
        runningStats.getBuildCount() >> 1
        runningStats.getPrettyUpTime() >> "3 mins"
        runningStats.getAllBuildsTime() >> 1000

        then:
        healthStats.healthInfo == String.format("Starting 2nd build in daemon [uptime: 3 mins, performance: 98%%, GC rate: %.2f/s, tenured heap usage: 10%% of %.1f kB]", 1.0, 1.0)
    }

    def "handles no garbage collection data"() {
        when:
        gcInfo.getCollectionTime() >> 25
        runningStats.getBuildCount() >> 1
        runningStats.getPrettyUpTime() >> "3 mins"
        runningStats.getAllBuildsTime() >> 1000

        gcMonitor.getTenuredStats() >> {
            Stub(GarbageCollectionStats) {
                getUsage() >> -1
                getMax() >> -1
                getRate() >> 0
            }
        }

        then:
        healthStats.healthInfo == "Starting 2nd build in daemon [uptime: 3 mins, performance: 98%, no major garbage collections]"
    }

}
