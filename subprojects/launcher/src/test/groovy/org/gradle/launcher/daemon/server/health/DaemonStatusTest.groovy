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
import spock.lang.Subject
import spock.lang.Unroll

import static org.gradle.launcher.daemon.server.health.DaemonStatus.TENURED_RATE_EXPIRE_AT
import static org.gradle.launcher.daemon.server.health.DaemonStatus.TENURED_USAGE_EXPIRE_AT

class DaemonStatusTest extends Specification {

    def stats = Mock(DaemonStats)
    @Subject status = new DaemonStatus(stats)
    def gcMonitor = Mock(GarbageCollectionMonitor)

    @Rule SetSystemProperties props = new SetSystemProperties()

    def "validates supplied tenured usage threshold value"() {
        System.setProperty(TENURED_USAGE_EXPIRE_AT, "foo")
        _ * stats.getGcMonitor() >> gcMonitor
        _ * gcMonitor.gcStrategy >> GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS

        when:
        status.isDaemonUnhealthy()

        then:
        def ex = thrown(GradleException)
        ex.message == "System property 'org.gradle.daemon.performance.tenured-usage-expire-at' has incorrect value: 'foo'. The value needs to be an integer."
    }

    def "validates supplied tenured rate threshold value"() {
        System.setProperty(TENURED_RATE_EXPIRE_AT, "foo")
        _ * stats.getGcMonitor() >> gcMonitor
        _ * gcMonitor.gcStrategy >> GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS

        when:
        status.isDaemonUnhealthy()

        then:
        def ex = thrown(GradleException)
        ex.message == "System property 'org.gradle.daemon.performance.tenured-rate-expire-at' has incorrect value: 'foo'. The value needs to be a double."
    }

    @Unroll
    def "knows when daemon is tired (#rateThreshold <= #rate, #usageThreshold <= #used)"() {
        _ * stats.getGcMonitor() >> gcMonitor
        _ * gcMonitor.gcStrategy >> GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS

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
        status.isDaemonUnhealthy() == tired

        where:
        rateThreshold | usageThreshold | rate | used | tired
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

    def "can disable daemon performance monitoring"() {
        when:
        System.setProperty(DaemonStatus.ENABLE_PERFORMANCE_MONITORING, "false")

        then:
        !status.isDaemonUnhealthy()
    }
}
