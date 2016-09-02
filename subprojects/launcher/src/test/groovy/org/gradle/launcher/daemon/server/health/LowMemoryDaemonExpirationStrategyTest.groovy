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

import com.google.common.base.Strings
import org.gradle.launcher.daemon.server.Daemon
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE

class LowMemoryDaemonExpirationStrategyTest extends Specification {
    private final Daemon daemon = Mock()
    private final MemoryInfo mockMemoryInfo = Mock(MemoryInfo)

    def "minimum threshold is enforced"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, 5)

        expect:
        expirationStrategy.memoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES
    }

    def "maximum threshold is enforced"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, 2 * LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES)

        expect:
        expirationStrategy.memoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES
    }

    def "daemon should expire when memory falls below threshold"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES)

        when:
        1 * mockMemoryInfo.getFreePhysicalMemory() >> { LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES - 1 }

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == GRACEFUL_EXPIRE
        result.reason == LowMemoryDaemonExpirationStrategy.EXPIRATION_REASON
    }

    def "daemon should not expire when memory is above threshold"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES)

        when:
        1 * mockMemoryInfo.getFreePhysicalMemory() >> { LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES + 1 }

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == DO_NOT_EXPIRE
        Strings.isNullOrEmpty(result.reason)
    }

    def "strategy computes total memory percentage"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES * 2 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(0.5, mockMemoryInfo)
        expirationStrategy.memoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES
    }

    def "strategy computes total memory percentage of zero"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES * 2 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(0, mockMemoryInfo)
        expirationStrategy.memoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES
    }

    def "strategy computes total memory percentage of one"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES * 2 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(1, mockMemoryInfo)
        expirationStrategy.memoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES
    }

    def "strategy does not accept negative threshold"() {
        when:
        new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, -1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept percentage less than 0"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(-0.1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept percentage greater than 1"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(1.1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept NaN percentage"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(Double.NaN)

        then:
        thrown IllegalArgumentException
    }
}
