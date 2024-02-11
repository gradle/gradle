/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import org.slf4j.Logger
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.IMMEDIATE_EXPIRE

/**
 * Tests {@link HealthExpirationStrategy}.
 */
class HealthExpirationStrategyTest extends Specification {

    @Rule
    SetSystemProperties props = new SetSystemProperties()

    GarbageCollectorMonitoringStrategy strategy = strategy(2.0, 80, 90, 5.0)
    GarbageCollectionStats belowThreshold = stats(1, 1, true)
    GarbageCollectionStats aboveHeapThreshold = stats(
        strategy.getHeapUsageThreshold() + 1,
        strategy.getGcRateThreshold() + 1,
        true
    )
    GarbageCollectionStats aboveThrashingThreshold = stats(
        strategy.getHeapUsageThreshold() + 1,
        strategy.getThrashingThreshold() + 1,
        true
    )
    GarbageCollectionStats aboveMetaspaceThreshold = stats(
        strategy.getNonHeapUsageThreshold() + 1,
        1,
        true
    )

    def "daemon is not expired when memory stats are below threshold" () {
        given:
        def underTest = new HealthExpirationStrategy(
            health(belowThreshold, belowThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result == DaemonExpirationResult.NOT_TRIGGERED
    }

    def "daemon is expired when garbage collector is thrashing" () {
        given:
        def underTest = new HealthExpirationStrategy(
            health(aboveThrashingThreshold, belowThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result.status == IMMEDIATE_EXPIRE
        result.reason == "since the JVM garbage collector is thrashing"
    }

    def "daemon is expired when heap space is low" () {
        given:
        def underTest = new HealthExpirationStrategy(
            health(aboveHeapThreshold, belowThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result.status == GRACEFUL_EXPIRE
        result.reason == "after running out of JVM heap space"
    }

    def "daemon is expired when metaspace is low" () {
        given:
        def underTest = new HealthExpirationStrategy(
            health(belowThreshold, aboveMetaspaceThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result.status == GRACEFUL_EXPIRE
        result.reason == "after running out of JVM Metaspace"
    }

    def "can disable daemon performance monitoring"() {
        given:
        System.setProperty(HealthExpirationStrategy.ENABLE_PERFORMANCE_MONITORING, "false")
        def underTest = new HealthExpirationStrategy(
            health(aboveHeapThreshold, aboveMetaspaceThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result == DaemonExpirationResult.NOT_TRIGGERED
    }

    def "can detect both heap space and metaspace conditions"() {
        given:
        def underTest = new HealthExpirationStrategy(
            health(aboveHeapThreshold, aboveMetaspaceThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result.status == GRACEFUL_EXPIRE
        result.reason == "after running out of JVM heap space and after running out of JVM Metaspace"
    }

    def "can detect both thrashing and metaspace conditions"() {
        given:
        def underTest = new HealthExpirationStrategy(
            health(aboveThrashingThreshold, aboveMetaspaceThreshold),
            strategy
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        result.status == IMMEDIATE_EXPIRE
        result.reason == "since the JVM garbage collector is thrashing and after running out of JVM Metaspace"
    }

    def "logs are not spammed if checkExpiration is called multiple times while an unhealthy condition persists"() {
        given:
        Logger logger = Mock(Logger)
        DaemonHealthStats stats = Mock(DaemonHealthStats)
        stats.getNonHeapStats() >> belowThreshold
        def underTest = new HealthExpirationStrategy(stats, strategy, logger)

        // If there is no unhealthy condition, we expect no logging.
        when:
        stats.getHeapStats() >> belowThreshold
        underTest.checkExpiration()

        then:
        0 * logger.warn(_)

        // After encountering the unhealthy condition for the first time, we log.
        when:
        stats.getHeapStats() >> aboveHeapThreshold
        underTest.checkExpiration()

        then:
        1 * logger.warn({ it.startsWith("The Daemon will expire after the build") })

        // Once encountered again, we expect no log.
        when:
        stats.getHeapStats() >> aboveHeapThreshold
        underTest.checkExpiration()

        then:
        0 * logger.warn(_)

        // When we encounter a more severe condition, we log again.
        when:
        stats.getHeapStats() >> aboveThrashingThreshold
        underTest.checkExpiration()

        then:
        1 * logger.warn({ it.startsWith("The Daemon will expire immediately") })

        // Encountering the more severe condition again does not log.
        when:
        stats.getHeapStats() >> aboveThrashingThreshold
        underTest.checkExpiration()

        then:
        0 * logger.warn(_)

        // And if we go back to a less severe condition, we do not log
        when:
        stats.getHeapStats() >> aboveHeapThreshold
        underTest.checkExpiration()

        then:
        0 * logger.warn(_)
    }

    def "knows when heap space is exhausted (#rateThreshold <= #rate, #usageThreshold <= #usage)"(double rateThreshold, int usageThreshold, double rate, int usage, boolean unhealthy) {
        given:
        def underTest = new HealthExpirationStrategy(
            Mock(DaemonHealthStats) {
                getHeapStats() >> stats(usage, rate, true)
                getNonHeapStats() >> stats(-1, -1, false)
            },
            strategy(rateThreshold, usageThreshold, -1, -1)
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        if (unhealthy) {
            assert result.status == GRACEFUL_EXPIRE
            assert result.reason == "after running out of JVM heap space"
        } else {
            assert result == DaemonExpirationResult.NOT_TRIGGERED
        }

        where:
        rateThreshold | usageThreshold | rate | usage | unhealthy
        1.0           | 90             | 1.1  | 100   | true
        1.0           | 90             | 1.1  | 91    | true
        1.0           | 90             | 1.1  | 89    | false
        1.0           | 90             | 0.9  | 91    | false
        1.0           | -1             | 1.0  | 0     | false
        -1            | 90             | 0    | 100   | false
        1.0           | -1             | 1.1  | 100   | false
        -1            | 90             | 1.1  | 100   | false
        1.0           | 100            | 1.1  | 100   | true
        1.0           | 75             | 1.1  | 75    | true
        1.0           | 75             | 1.0  | 100   | true
    }

    def "knows when metaspace is exhausted (#usageThreshold <= #usage, #usageThreshold <= #usage)"() {
        given:
        def underTest = new HealthExpirationStrategy(
            Mock(DaemonHealthStats) {
                getHeapStats() >> stats(-1, -1, false)
                getNonHeapStats() >> stats(usage, -1, true)
            },
            strategy(-1, -1, usageThreshold, -1)
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        if (unhealthy) {
            assert result.status == GRACEFUL_EXPIRE
            assert result.reason == "after running out of JVM Metaspace"
        } else {
            assert result == DaemonExpirationResult.NOT_TRIGGERED
        }

        where:
        usageThreshold | usage | unhealthy
        90             | 100   | true
        90             | 91    | true
        90             | 90    | true
        90             | 89    | false
        -1             | 0     | false
        -1             | 100   | false
        100            | 100   | true
    }

    def "knows when gc is thrashing (#rateThreshold <= #rate) #usageThreshold #usage #thrashing"(double rateThreshold, int usageThreshold, double rate, int usage, boolean thrashing) {
        given:
        def underTest = new HealthExpirationStrategy(
            Mock(DaemonHealthStats) {
                getHeapStats() >> stats(usage, rate, true)
                getNonHeapStats() >> stats(-1, -1, false)
            },
            strategy(-1, usageThreshold, -1, rateThreshold)
        )

        when:
        DaemonExpirationResult result = underTest.checkExpiration()

        then:
        if (thrashing) {
            assert result.status == IMMEDIATE_EXPIRE
            assert result.reason == "since the JVM garbage collector is thrashing"
        } else {
            assert result == DaemonExpirationResult.NOT_TRIGGERED
        }

        where:
        rateThreshold | usageThreshold | rate | usage | thrashing
        10            | 90             | 15   | 100   | true
        10            | 90             | 10.1 | 91    | true
        10            | 90             | 10.1 | 89    | false
        10            | 90             | 9.9  | 91    | false
        10            | -1             | 15   | 0     | false
        -1            | 90             | 0    | 100   | false
        10            | -1             | 10.1 | 100   | false
        -1            | 90             | 10.1 | 100   | false
        10            | 100            | 10.1 | 100   | true
        10            | 75             | 10.1 | 75    | true
        10            | 75             | 10   | 100   | true
        10            | 90             | 0    | 100   | false
        10            | 90             | 15   | 0     | false
    }

    GarbageCollectionStats stats(int percent, double rate, boolean valid) {
        return Stub(GarbageCollectionStats) {
            getUsedPercent() >> percent
            getGcRate() >> rate
            isValid() >> valid
            getEventCount() >> (valid ? 5 : 0)
        }
    }

    GarbageCollectorMonitoringStrategy strategy(double gcRateThreshold, int heapUsageThreshold, int nonHeapUsageThreshold, double thrashingThreshold) {
        return new GarbageCollectorMonitoringStrategy(null, null, null, gcRateThreshold, heapUsageThreshold, nonHeapUsageThreshold, thrashingThreshold)
    }

    def health(GarbageCollectionStats heap, GarbageCollectionStats metaspace) {
        return Stub(DaemonHealthStats) {
            getHeapStats() >> heap
            getNonHeapStats() >> metaspace
        }
    }
}
