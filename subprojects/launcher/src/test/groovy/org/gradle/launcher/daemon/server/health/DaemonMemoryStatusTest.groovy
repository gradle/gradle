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

import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DaemonMemoryStatusTest extends Specification {
    @Rule
    SetSystemProperties props = new SetSystemProperties()

    def stats = Mock(DaemonHealthStats)
    DaemonMemoryStatus create(int heapUsageThreshold, double heapRateThreshold, int nonHeapUsageThreshold, double thrashingThreshold) {
        return new DaemonMemoryStatus(stats, heapUsageThreshold, heapRateThreshold, nonHeapUsageThreshold, thrashingThreshold)
    }

    @Unroll
    def "knows when heap space is exhausted (#rateThreshold <= #rate, #usageThreshold <= #usage)"(double rateThreshold, int usageThreshold, double rate, int usage, boolean unhealthy) {
        when:
        def status = create(usageThreshold, rateThreshold, 100, 100)
        stats.getHeapStats() >> {
            new GarbageCollectionStats(rate, usage, 100, 10)
        }

        then:
        status.isHeapSpaceExhausted() == unhealthy

        where:
        rateThreshold | usageThreshold | rate | usage | unhealthy
        1.0           | 90             | 1.1  | 100   | true
        1.0           | 90             | 1.1  | 91    | true
        1.0           | 90             | 1.1  | 89    | false
        1.0           | 90             | 0.9  | 91    | false
        1.0           | 0              | 1.0  | 0     | false
        0             | 90             | 0    | 100   | false
        1.0           | 0              | 1.1  | 100   | false
        0             | 90             | 1.1  | 100   | false
        1.0           | 100            | 1.1  | 100   | true
        1.0           | 75             | 1.1  | 75    | true
        1.0           | 75             | 1.0  | 100   | true
    }

    @Unroll
    def "knows when metaspace is exhausted (#usageThreshold <= #usage, #rateThreshold <= #rate)"(double rateThreshold, int usageThreshold, double rate, int usage, boolean unhealthy) {
        when:
        def status = create(100, rateThreshold, usageThreshold, 100)
        stats.getHeapStats() >> {
            new GarbageCollectionStats(rate, 0, 100, 10)
        }
        stats.getNonHeapStats() >> {
            new GarbageCollectionStats(0, usage, 100, 10)
        }

        then:
        status.isNonHeapSpaceExhausted() == unhealthy

        where:
        rateThreshold | usageThreshold | rate | usage | unhealthy
        1.0           | 90             | 1.1  | 100   | true
        1.0           | 90             | 1.1  | 91    | true
        1.0           | 90             | 1.1  | 89    | false
        1.0           | 90             | 0.9  | 91    | false
        1.0           | 0              | 1.0  | 0     | false
        0             | 90             | 0    | 100   | false
        1.0           | 0              | 1.1  | 100   | false
        0             | 90             | 1.1  | 100   | false
        1.0           | 100            | 1.1  | 100   | true
        1.0           | 75             | 1.1  | 75    | true
        1.0           | 75             | 1.0  | 100   | true
    }

    @Unroll
    def "knows when gc is thrashing (#rateThreshold <= #rate) #usageThreshold #usage #thrashing"(double rateThreshold, int usageThreshold, double rate, int usage, boolean thrashing) {
        when:
        def status = create(usageThreshold, 100, 100, rateThreshold)
        stats.getHeapStats() >> {
            new GarbageCollectionStats(rate, usage, 100, 10)
        }

        then:
        status.isThrashing() == thrashing

        where:
        rateThreshold | usageThreshold | rate | usage | thrashing
        10            | 90             | 15   | 100   | true
        10            | 90             | 10.1 | 91    | true
        10            | 90             | 10.1 | 89    | false
        10            | 90             | 9.9  | 91    | false
        10            | 0              | 15   | 0     | false
        0             | 90             | 0    | 100   | false
        10            | 0              | 10.1 | 100   | false
        0             | 90             | 10.1 | 100   | false
        10            | 100            | 10.1 | 100   | true
        10            | 75             | 10.1 | 75    | true
        10            | 75             | 10   | 100   | true
        10            | 90             | 0    | 100   | false
        10            | 90             | 15   | 0     | false
    }

    def "can disable daemon performance monitoring"() {
        when:
        def status = create(100, 100, 100, 100)
        System.setProperty(DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING, "false")

        then:
        !status.isHeapSpaceExhausted()

        and:
        !status.isNonHeapSpaceExhausted()

        and:
        !status.isThrashing()
    }
}
