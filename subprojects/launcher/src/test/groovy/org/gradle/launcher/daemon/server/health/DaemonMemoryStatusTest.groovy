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

import org.gradle.api.GradleException
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static DaemonMemoryStatus.PERMGEN_USAGE_EXPIRE_AT
import static DaemonMemoryStatus.TENURED_RATE_EXPIRE_AT
import static DaemonMemoryStatus.TENURED_USAGE_EXPIRE_AT
import static DaemonMemoryStatus.THRASHING_EXPIRE_AT

class DaemonMemoryStatusTest extends Specification {
    @Rule SetSystemProperties props = new SetSystemProperties()

    def gcMonitor = Mock(GarbageCollectionMonitor)
    def stats = Mock(DaemonHealthStats)

    def "validates supplied tenured usage threshold value"() {
        System.setProperty(TENURED_USAGE_EXPIRE_AT, "foo")

        when:
        status.isTenuredSpaceExhausted()

        then:
        def ex = thrown(GradleException)
        ex.message == "System property 'org.gradle.daemon.performance.tenured-usage-expire-at' has incorrect value: 'foo'. The value needs to be an integer."
    }

    def "validates supplied tenured rate threshold value"() {
        System.setProperty(TENURED_RATE_EXPIRE_AT, "foo")

        when:
        status.isTenuredSpaceExhausted()

        then:
        def ex = thrown(GradleException)
        ex.message == "System property 'org.gradle.daemon.performance.tenured-rate-expire-at' has incorrect value: 'foo'. The value needs to be a double."
    }

    @Unroll
    def "knows when tenured space is exhausted (#rateThreshold <= #rate, #usageThreshold <= #used)"() {
        when:
        System.setProperty(TENURED_USAGE_EXPIRE_AT, usageThreshold.toString())
        System.setProperty(TENURED_RATE_EXPIRE_AT, rateThreshold.toString())
        gcMonitor.getTenuredStats() >> {
            Stub(GarbageCollectionStats) {
                getUsage() >> used
                getRate() >> rate
                getEventCount() >> 10
            }
        }

        then:
        status.isTenuredSpaceExhausted() == unhealthy

        where:
        rateThreshold | usageThreshold | rate | used | unhealthy
        1.0           | 90             | 1.1  | 100  | true
        1.0           | 90             | 1.1  | 91   | true
        1.0           | 90             | 1.1  | 89   | false
        1.0           | 90             | 0.9  | 91   | false
        1.0           | 0              | 1.0  | 0    | false
        1.0           | 0              | 1.0  | -1   | false
        0             | 90             | 0    | 100  | false
        1.0           | 0              | 1.1  | 100  | false
        0             | 90             | 1.1  | 100  | false
        1.0           | 100            | 1.1  | 100  | true
        1.0           | 75             | 1.1  | 75   | true
        1.0           | 75             | 1.0  | 100  | true
    }

    @Unroll
    def "knows when perm gen space is exhausted (#usageThreshold <= #used, #usageThreshold <= #used)"() {
        when:
        System.setProperty(PERMGEN_USAGE_EXPIRE_AT, usageThreshold.toString())
        gcMonitor.getPermGenStats() >> {
            Stub(GarbageCollectionStats) {
                getUsage() >> used
                getEventCount() >> 10
            }
        }

        then:
        status.isPermGenSpaceExhausted() == unhealthy

        where:
        usageThreshold | used | unhealthy
        90             | 100  | true
        90             | 91   | true
        90             | 90   | true
        90             | 89   | false
        0              | 0    | false
        0              | 100  | false
        100            | 100  | true
    }

    @Unroll
    def "knows when gc is thrashing (#rateThreshold <= #rate)"() {
        when:
        System.setProperty(TENURED_USAGE_EXPIRE_AT, usageThreshold.toString())
        System.setProperty(THRASHING_EXPIRE_AT, rateThreshold.toString())
        gcMonitor.getTenuredStats() >> {
            Stub(GarbageCollectionStats) {
                getRate() >> rate
                getUsage() >> usage
                getEventCount() >> 10
            }
        }

        then:
        status.isThrashing() == thrashing

        where:
        rateThreshold  | usageThreshold | rate | usage | thrashing
        10             | 90             | 15   | 100  | true
        10             | 90             | 10.1 | 91   | true
        10             | 90             | 10.1 | 89   | false
        10             | 90             | 9.9  | 91   | false
        10             | 0              | 15   | 0    | false
        0              | 90             | 0    | 100  | false
        10             | 0              | 10.1 | 100  | false
        0              | 90             | 10.1 | 100  | false
        10             | 100            | 10.1 | 100  | true
        10             | 75             | 10.1 | 75   | true
        10             | 75             | 10   | 100  | true
        10             | 90             | 0    | 100  | false
        10             | 90             | 15   | 0    | false
    }

    def "can disable daemon performance monitoring"() {
        when:
        System.setProperty(DaemonMemoryStatus.ENABLE_PERFORMANCE_MONITORING, "false")

        then:
        !status.isTenuredSpaceExhausted()

        and:
        !status.isPermGenSpaceExhausted()

        and:
        !status.isThrashing()
    }

    DaemonMemoryStatus getStatus() {
        1 * gcMonitor.gcStrategy >> GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS
        _ * stats.getGcMonitor() >> gcMonitor
        return new DaemonMemoryStatus(stats)
    }
}
