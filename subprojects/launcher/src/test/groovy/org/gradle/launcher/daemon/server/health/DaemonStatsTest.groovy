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
import org.gradle.util.Clock
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

class DaemonStatsTest extends Specification {

    def clock = Stub(Clock)
    def time = Stub(TimeProvider)
    def memory = Stub(MemoryInfo)

    def "consumes first build"() {
        def stats = new DaemonStats(clock, Stub(TimeProvider), memory)
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
        memory.getCommittedMemory() >> 5000000
        memory.getMaxMemory() >> 10000000

        def stats = new DaemonStats(clock, time, memory)

        when:
        stats.buildStarted()
        stats.buildFinished()
        stats.buildStarted()
        stats.buildFinished()

        then:
        stats.healthInfo == String.format("Starting 2nd build in daemon [uptime: 3 mins, performance: 98%%, memory: 50%% of %.1f MB]", 10.0)
    }

    def "time might go backwards"() {
        clock = new Clock(time)
        def currentTime = new AtomicLong()
        currentTime.set(1001)
        time.getCurrentTime() >> { currentTime.get() }

        memory.getCollectionTime() >> 25
        memory.getCommittedMemory() >> 5000000
        memory.getMaxMemory() >> 10000000

        def stats = new DaemonStats(clock, time, memory)

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
        stats.healthInfo == String.format("Starting 2nd build in daemon [uptime: %s, performance: 98%%, memory: 50%% of %.1f MB]", Clock.prettyTime(1), 10.0)
    }
}
