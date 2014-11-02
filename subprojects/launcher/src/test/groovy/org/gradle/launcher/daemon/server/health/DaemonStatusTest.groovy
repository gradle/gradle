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

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class DaemonStatusTest extends Specification {

    @Subject status = new DaemonStatus()
    def stats = Mock(DaemonStats)

    @Rule SetSystemProperties props = new SetSystemProperties()

    def "uses default tired threshold"() {
        stats.getCurrentPerformance() >> 85
        def betterStats = Mock(DaemonStats) { getCurrentPerformance() >> 86 }

        expect:
        status.isDaemonTired(stats)
        !status.isDaemonTired(betterStats)
    }

    def "knows when daemon is tired"() {
        when:
        System.setProperty(DaemonStatus.EXPIRE_AT_PROPERTY, threshold.toString())
        stats.getCurrentPerformance() >> perf

        then:
        status.isDaemonTired(stats) >> tired

        where:
        threshold | perf | tired
        90        | 89   | true
        90        | 90   | true
        90        | 91   | false
        0         | 0    | false
        0         | 1    | false
        100       | 100  | true
    }
}
