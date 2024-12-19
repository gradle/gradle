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

package org.gradle.api.internal.cache

import org.gradle.api.cache.Cleanup
import org.gradle.api.cache.MarkingStrategy
import org.gradle.cache.internal.LegacyCacheCleanupEnablement
import org.gradle.internal.time.FixedClock
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DefaultCacheConfigurationsTest extends Specification {
    def clock = FixedClock.create()
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Mock(LegacyCacheCleanupEnablement), clock)

    def "timestamp supplier uses last configured retention value"() {
        def config = cacheConfigurations.buildCache
        def configDefault = daysToMillis(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES)

        expect:
        config.entryRetentionTimestampSupplier.get() == clock.currentTime - configDefault

        when:
        config.removeUnusedEntriesAfterDays = 3

        then:
        config.entryRetentionTimestampSupplier.get() == clock.currentTime - daysToMillis(3)

        when:
        config.removeUnusedEntriesOlderThan = 1000

        then:
        config.entryRetentionTimestampSupplier.get() == 1000

        when:
        config.removeUnusedEntriesAfterDays = 4

        then:
        config.entryRetentionTimestampSupplier.get() == clock.currentTime - daysToMillis(4)

        when:
        config.entryRetention.unset()

        then:
        config.entryRetentionTimestampSupplier.get() ==  clock.currentTime - configDefault
    }

    def "cannot modify cache configurations via convenience method unless mutable"() {
        when:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.buildCache.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.cleanup.set(Cleanup.DISABLED)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurationValues()

        and:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        def e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.buildCache.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")
    }

    def "cannot modify cache configurations via property unless mutable (method: #method)"() {
        def firstValue = CacheResourceConfigurationInternal.EntryRetention.absolute(100)
        def secondValue = CacheResourceConfigurationInternal.EntryRetention.absolute(200)

        when:
        cacheConfigurations.createdResources.entryRetention."${method}"(firstValue)
        cacheConfigurations.downloadedResources.entryRetention."${method}"(firstValue)
        cacheConfigurations.releasedWrappers.entryRetention."${method}"(firstValue)
        cacheConfigurations.snapshotWrappers.entryRetention."${method}"(firstValue)
        cacheConfigurations.buildCache.entryRetention."${method}"(firstValue)
        cacheConfigurations.cleanup."${method}"(Cleanup.DISABLED)
        cacheConfigurations.markingStrategy."${method}"(MarkingStrategy.NONE)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurationValues()

        and:
        cacheConfigurations.createdResources.entryRetention."${method}"(secondValue)

        then:
        def e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.downloadedResources.entryRetention."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.releasedWrappers.entryRetention."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.snapshotWrappers.entryRetention."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.buildCache.entryRetention."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetention")

        when:
        cacheConfigurations.cleanup."${method}"(Cleanup.DEFAULT)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "cleanup")

        when:
        cacheConfigurations.markingStrategy."${method}"(MarkingStrategy.CACHEDIR_TAG)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "markingStrategy")

        where:
        method << ["set", "value", "convention"]
    }

    def "suppliers reflect changed property value relative to reference timestamp"() {
        when:
        def createdResources = cacheConfigurations.createdResources.entryRetentionTimestampSupplier
        def downloadedResources = cacheConfigurations.downloadedResources.entryRetentionTimestampSupplier
        def releasedWrappers = cacheConfigurations.releasedWrappers.entryRetentionTimestampSupplier
        def snapshotWrappers = cacheConfigurations.snapshotWrappers.entryRetentionTimestampSupplier
        def buildCache = cacheConfigurations.buildCache.entryRetentionTimestampSupplier


        and:
        def twoDaysMillis = TimeUnit.DAYS.toMillis(2)
        def twoDaysRetention = CacheResourceConfigurationInternal.EntryRetention.relative(twoDaysMillis)
        cacheConfigurations.createdResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.downloadedResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.releasedWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.snapshotWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.buildCache.entryRetention.set(twoDaysRetention)

        then:
        def twoDaysAgo = clock.currentTime - twoDaysMillis
        createdResources.get() == twoDaysAgo
        downloadedResources.get() == twoDaysAgo
        releasedWrappers.get() == twoDaysAgo
        snapshotWrappers.get() == twoDaysAgo
        buildCache.get() == twoDaysAgo
    }

    def "cannot set values in days to less than one"() {
        when:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(0)

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(0)

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(0)

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(0)

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.buildCache.setRemoveUnusedEntriesAfterDays(0)

        then:
        thrown(IllegalArgumentException)
    }

    def "synchronized configurations reflect changes in property values"() {
        def mutableCacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations, Mock(LegacyCacheCleanupEnablement), clock)

        when:
        cacheConfigurations.synchronize(mutableCacheConfigurations)

        and:
        def twoDays = TimeUnit.DAYS.toMillis(2)
        def twoDaysRetention = CacheResourceConfigurationInternal.EntryRetention.relative(twoDays)
        cacheConfigurations.createdResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.downloadedResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.releasedWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.snapshotWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.buildCache.entryRetention.set(twoDaysRetention)
        cacheConfigurations.markingStrategy.set(MarkingStrategy.NONE)

        then:
        mutableCacheConfigurations.createdResources.entryRetention.get() == twoDaysRetention
        mutableCacheConfigurations.downloadedResources.entryRetention.get() == twoDaysRetention
        mutableCacheConfigurations.releasedWrappers.entryRetention.get() == twoDaysRetention
        mutableCacheConfigurations.snapshotWrappers.entryRetention.get() == twoDaysRetention
        mutableCacheConfigurations.buildCache.entryRetention.get() == twoDaysRetention
        mutableCacheConfigurations.markingStrategy.get() == MarkingStrategy.NONE
    }

    def "will not require cleanup unless configured"() {
        when:
        def twoDays = TimeUnit.DAYS.toMillis(2)
        def twoDaysRetention = CacheResourceConfigurationInternal.EntryRetention.relative(twoDays)
        cacheConfigurations.createdResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.downloadedResources.entryRetention.set(twoDaysRetention)
        cacheConfigurations.releasedWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.snapshotWrappers.entryRetention.set(twoDaysRetention)
        cacheConfigurations.buildCache.entryRetention.set(twoDaysRetention)
        cacheConfigurations.cleanup.set(Cleanup.ALWAYS)

        then:
        !cacheConfigurations.cleanupFrequency.get().requiresCleanup(null)

        when:
        cacheConfigurations.cleanupHasBeenConfigured = true

        then:
        cacheConfigurations.cleanupFrequency.get().requiresCleanup(null)
    }

    void assertCannotConfigureErrorIsThrown(Exception e, String name) {
        assert e.message.contains(String.format(DefaultCacheConfigurations.UNSAFE_MODIFICATION_ERROR, name))
    }

    def daysToMillis(int days) {
        return TimeUnit.DAYS.toMillis(days)
    }
}
