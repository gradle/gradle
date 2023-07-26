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
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.process.internal.health.memory.DefaultLimitedOsMemoryCategory
import org.gradle.process.internal.health.memory.DefaultUnknownOsMemoryCategory
import org.gradle.process.internal.health.memory.OsMemoryStatus
import spock.lang.Specification

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE
import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE

class LowMemoryDaemonExpirationStrategyTest extends Specification {
    private static final long ONE_GIG = 1024 * 1024 * 1024
    private static final long MAX_MEMORY = 16 * ONE_GIG
    private final OsMemoryStatus mockMemoryStatus = Mock(OsMemoryStatus)

    def "minimum threshold is enforced"() {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(0)

        when:
        expirationStrategy.onOsMemoryStatus(mockMemoryStatus)

        then:
        expirationStrategy.physicalMemoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MIN_THRESHOLD_BYTES
    }

    def "maximum threshold is enforced"() {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(1)

        when:
        expirationStrategy.onOsMemoryStatus(mockMemoryStatus)

        then:
        expirationStrategy.physicalMemoryThresholdInBytes == LowMemoryDaemonExpirationStrategy.MAX_THRESHOLD_BYTES
    }

    def "daemon should expire when physical memory falls below threshold"() {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(1)

        when:
        1 * mockMemoryStatus.physicalMemory >> { new DefaultLimitedOsMemoryCategory("physical", MAX_MEMORY, 0L) }
        1 * mockMemoryStatus.virtualMemory >> { new DefaultUnknownOsMemoryCategory("virtual") }
        expirationStrategy.onOsMemoryStatus(mockMemoryStatus)

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == GRACEFUL_EXPIRE
        result.reason == "to reclaim physical system memory"
    }

    def "daemon should expire when virtual memory falls below threshold"() {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(1)

        when:
        1 * mockMemoryStatus.physicalMemory >> { new DefaultLimitedOsMemoryCategory("physical", MAX_MEMORY, ONE_GIG) }
        1 * mockMemoryStatus.virtualMemory >> { new DefaultLimitedOsMemoryCategory("virtual", MAX_MEMORY, 0L) }
        expirationStrategy.onOsMemoryStatus(mockMemoryStatus)

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == GRACEFUL_EXPIRE
        result.reason == "to reclaim virtual system memory"
    }

    def "daemon should not expire when memory is above threshold"() {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(0)

        when:
        1 * mockMemoryStatus.physicalMemory >> { new DefaultLimitedOsMemoryCategory("physical", MAX_MEMORY, ONE_GIG) }
        1 * mockMemoryStatus.virtualMemory >> { new DefaultUnknownOsMemoryCategory("virtual") }
        expirationStrategy.onOsMemoryStatus(mockMemoryStatus)

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == DO_NOT_EXPIRE
        Strings.isNullOrEmpty(result.reason)
    }

    def "strategy does not accept negative threshold"() {
        when:
        new LowMemoryDaemonExpirationStrategy(-1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept percentage greater than 1"() {
        when:
        new LowMemoryDaemonExpirationStrategy(1.1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept NaN percentage"() {
        when:
        new LowMemoryDaemonExpirationStrategy(Double.NaN)

        then:
        thrown IllegalArgumentException
    }

    def "does not expire when no memory status notification is received" () {
        given:
        def expirationStrategy = new LowMemoryDaemonExpirationStrategy(1)
        _ * mockMemoryStatus.physicalMemory >> { new DefaultLimitedOsMemoryCategory("physical", MAX_MEMORY, 0L) }
        _ * mockMemoryStatus.virtualMemory >> { new DefaultUnknownOsMemoryCategory("virtual") }

        expect:
        DaemonExpirationResult result = expirationStrategy.checkExpiration()
        result.status == DO_NOT_EXPIRE
        Strings.isNullOrEmpty(result.reason)
    }
}
