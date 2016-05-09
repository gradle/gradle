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

import org.gradle.internal.TimeProvider
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.util.Clock
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

class DaemonStatsTest extends Specification {

    def clock = Stub(Clock)
    def time = Stub(TimeProvider)
    def memory = Stub(MemoryInfo)
    def gcMonitor = Stub(GarbageCollectionMonitor)

    def "consumes first build"() {
        def stats = new DaemonStats(clock, Stub(TimeProvider), memory, gcMonitor)
        memory.getCommittedMemory() >> 5000000
        memory.getMaxMemory() >> 10000000

        when:
        stats.buildStarted()
        stats.buildFinished()

        then:
        stats.healthInfo == String.format("Starting build in new daemon [memory: %.1f MB]", 10.0)
    }

    def "consumes subsequent builds"() {
        clock.getTime() >> "3 mins"
        time.getCurrentTime() >>> [1, 1001]

        memory.getCollectionTime() >> 25

        gcMonitor.getTenuredStats() >> {
            Stub(GarbageCollectionStats) {
                getUsage() >> 10
                getMax() >> 1024
                getRate() >> 1.0
                getCount() >> 100
            }
        }

        def stats = new DaemonStats(clock, time, memory, gcMonitor)

        when:
        stats.buildStarted()
        stats.buildFinished()
        stats.buildStarted()
        stats.buildFinished()

        then:
        stats.healthInfo == String.format("Starting 2nd build in daemon [uptime: 3 mins, performance: 98%%, GC rate: %.2f/s, tenured heap usage: 10%% of %.1f kB]", 1.0, 1.0)
    }

    def "time might go backwards"() {
        clock = new Clock(time)
        def currentTime = new AtomicLong()
        currentTime.set(1001)
        time.getCurrentTime() >> { currentTime.get() }

        memory.getCollectionTime() >> 25
        memory.getCommittedMemory() >> 5000000
        memory.getMaxMemory() >> 10000000

        def stats = new DaemonStats(clock, time, memory, gcMonitor)

        when:
        stats.buildStarted()
        currentTime.set(2001)
        stats.buildFinished()

        then:
        stats.healthInfo == String.format("Starting build in new daemon [memory: %.1f MB]", 10.0)

        when:
        stats.buildStarted()
        currentTime.set(1)
        stats.buildFinished()

        then:
        stats.healthInfo == String.format("Starting 2nd build in daemon [uptime: %s, performance: 98%%, GC rate: %.2f/s, tenured heap usage: 0%% of 0 B]", Clock.prettyTime(1), 0.0)
    }
}
