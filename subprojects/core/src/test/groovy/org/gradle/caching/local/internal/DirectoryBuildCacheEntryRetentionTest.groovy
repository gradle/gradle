/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local.internal

import org.gradle.api.cache.Cleanup
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.cache.CacheResourceConfigurationInternal
import org.gradle.api.internal.cache.DefaultCacheConfigurations
import org.gradle.cache.internal.LegacyCacheCleanupEnablement
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.internal.time.FixedClock
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DirectoryBuildCacheEntryRetentionTest extends Specification {
    def clock = FixedClock.create()
    def directoryBuildCache = Mock(DirectoryBuildCache)
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Mock(LegacyCacheCleanupEnablement), clock)

    def "setup"() {
        cacheConfigurations.cleanup.set(Cleanup.DEFAULT)
    }

    def "uses directory build cache expiry when set to value other than default"() {
        when:
        cacheConfigurations.buildCache.removeUnusedEntriesAfterDays = 1

        and:
        1 * directoryBuildCache.getRemoveUnusedEntriesAfterDays() >> 10
        0 * _

        then:
        def expiration = new DirectoryBuildCacheEntryRetention(directoryBuildCache, cacheConfigurations)
        equalWithinOneSecond(expiration.entryRetentionTimestampSupplier.get(), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10))
        expiration.description == "after 10 days"
    }

    def "uses cache retention configured for #description"() {
        when:
        cacheConfigurations.buildCache.removeUnusedEntriesAfterDays = expiryDays

        and:
        1 * directoryBuildCache.getRemoveUnusedEntriesAfterDays() >> CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES
        0 * _

        then:
        def expiration = new DirectoryBuildCacheEntryRetention(directoryBuildCache, cacheConfigurations)
        expiration.entryRetentionTimestampSupplier.get() == clock.currentTime - TimeUnit.DAYS.toMillis(expiryDays)
        expiration.description == description

        where:
        expiryDays | description
        12         | "after 12 days"
        1          | "after 24h"
    }

    def "uses cache retention set to absolute timestamp"() {
        when:
        def timestamp = ZonedDateTime.of(2024, 11, 10, 9,35, 44, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        cacheConfigurations.buildCache.removeUnusedEntriesOlderThan = timestamp

        and:
        1 * directoryBuildCache.getRemoveUnusedEntriesAfterDays() >> CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES
        0 * _

        then:
        def expiration = new DirectoryBuildCacheEntryRetention(directoryBuildCache, cacheConfigurations)
        expiration.entryRetentionTimestampSupplier.get() == timestamp
        expiration.description == "older than 2024-11-10 09:35:44 UTC"
    }

    def "describes entry expiration less than one day"() {
        given:
        def cacheExpirySecs = 2 * 3600 + 25 * 60 + 44

        when:
        cacheConfigurations.buildCache.entryRetention.set(CacheResourceConfigurationInternal.EntryRetention.relative(cacheExpirySecs * 1000))

        and:
        1 * directoryBuildCache.getRemoveUnusedEntriesAfterDays() >> CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES
        0 * _

        then:
        def expiration = new DirectoryBuildCacheEntryRetention(directoryBuildCache, cacheConfigurations)
        expiration.entryRetentionTimestampSupplier.get() == clock.currentTime - cacheExpirySecs * 1000
        expiration.description == "after 2h 25m 44s"
    }

    def "describes entry expiration when cleanup is disabled with #configType config"() {
        when:
        cacheConfigurations.buildCache.removeUnusedEntriesAfterDays = 1
        cacheConfigurations.cleanup.set(Cleanup.DISABLED)

        and:
        1 * directoryBuildCache.getRemoveUnusedEntriesAfterDays() >> removeUnusedEntriesAfterDays
        0 * _

        then:
        def expiration = new DirectoryBuildCacheEntryRetention(directoryBuildCache, cacheConfigurations)
        expiration.description == "disabled"

        where:
        configType    | removeUnusedEntriesAfterDays
        "legacy"      | 10
        "init-script" | CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES
    }

    // Since we are relying on System.currentTimeMillis(), we can't assert the exact value
    private static equalWithinOneSecond(long actualMillis, long expectedMillis) {
        return Math.abs(expectedMillis - actualMillis) < 1000
    }
}
