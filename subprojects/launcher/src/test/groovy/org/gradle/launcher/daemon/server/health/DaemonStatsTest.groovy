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

class DaemonStatsTest extends Specification {

    def clock = Stub(Clock)
    def time = Stub(TimeProvider)
    def gcStats = Stub(GCStats)

    def "consumes first build"() {
        def stats = new DaemonStats(5000000, 10000000, clock, Stub(TimeProvider), gcStats)

        when:
        def greeting = stats.buildStarted()
        stats.buildFinished()

        then:
        greeting == "Starting build in new daemon [memory: 10.0 MB]"
    }

    def "consumes subsequent builds"() {
        clock.getTime() >> "3 mins"
        time.getCurrentTime() >>> [1, 1001]
        gcStats.getCollectionTime() >> 25

        def stats = new DaemonStats(5000000, 10000000, clock, time, gcStats)

        when:
        stats.buildStarted()
        stats.buildFinished()
        def greeting = stats.buildStarted()
        stats.buildFinished()

        then:
        greeting == "Executing 2nd build in daemon [uptime: 3 mins, performance: 98%, memory: 50% of 10.0 MB]"
    }
}
