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
import org.gradle.internal.time.Clock
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DefaultCacheConfigurationsTest extends Specification {
    def clock = new FixedClock()
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Mock(LegacyCacheCleanupEnablement), clock)

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
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.buildCache.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")
    }

    def "cannot modify cache configurations via property unless mutable (method: #method)"() {
        long firstValue = 2
        long secondValue = 1

        when:
        cacheConfigurations.createdResources.entryRetentionAge."${method}"(firstValue)
        cacheConfigurations.downloadedResources.entryRetentionAge."${method}"(firstValue)
        cacheConfigurations.releasedWrappers.entryRetentionAge."${method}"(firstValue)
        cacheConfigurations.snapshotWrappers.entryRetentionAge."${method}"(firstValue)
        cacheConfigurations.buildCache.entryRetentionAge."${method}"(firstValue)
        cacheConfigurations.cleanup."${method}"(Cleanup.DISABLED)
        cacheConfigurations.markingStrategy."${method}"(MarkingStrategy.NONE)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurationValues()

        and:
        cacheConfigurations.createdResources.entryRetentionAge."${method}"(secondValue)

        then:
        def e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.downloadedResources.entryRetentionAge."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.releasedWrappers.entryRetentionAge."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.snapshotWrappers.entryRetentionAge."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.buildCache.entryRetentionAge."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionAge")

        when:
        cacheConfigurations.buildCache.entryRetentionTimestamp."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "entryRetentionTimestamp")

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
        def twoDays = TimeUnit.DAYS.toMillis(2)
        cacheConfigurations.createdResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.downloadedResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.releasedWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.snapshotWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.buildCache.entryRetentionAge.set(twoDays)

        then:
        def twoDaysAgo = clock.currentTime - twoDays
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
        def threeDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        cacheConfigurations.createdResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.downloadedResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.releasedWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.snapshotWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.buildCache.entryRetentionAge.set(twoDays)
        cacheConfigurations.createdResources.entryRetentionTimestamp.set(threeDaysAgo)
        cacheConfigurations.downloadedResources.entryRetentionTimestamp.set(threeDaysAgo)
        cacheConfigurations.releasedWrappers.entryRetentionTimestamp.set(threeDaysAgo)
        cacheConfigurations.snapshotWrappers.entryRetentionTimestamp.set(threeDaysAgo)
        cacheConfigurations.buildCache.entryRetentionTimestamp.set(threeDaysAgo)
        cacheConfigurations.markingStrategy.set(MarkingStrategy.NONE)

        then:
        mutableCacheConfigurations.createdResources.entryRetentionAge.get() == twoDays
        mutableCacheConfigurations.downloadedResources.entryRetentionAge.get() == twoDays
        mutableCacheConfigurations.releasedWrappers.entryRetentionAge.get() == twoDays
        mutableCacheConfigurations.snapshotWrappers.entryRetentionAge.get() == twoDays
        mutableCacheConfigurations.buildCache.entryRetentionAge.get() == twoDays
        mutableCacheConfigurations.createdResources.entryRetentionTimestamp.get() == threeDaysAgo
        mutableCacheConfigurations.downloadedResources.entryRetentionTimestamp.get() == threeDaysAgo
        mutableCacheConfigurations.releasedWrappers.entryRetentionTimestamp.get() == threeDaysAgo
        mutableCacheConfigurations.snapshotWrappers.entryRetentionTimestamp.get() == threeDaysAgo
        mutableCacheConfigurations.buildCache.entryRetentionTimestamp.get() == threeDaysAgo
        mutableCacheConfigurations.markingStrategy.get() == MarkingStrategy.NONE
    }

    def "will not require cleanup unless configured"() {
        when:
        def twoDays = TimeUnit.DAYS.toMillis(2)
        cacheConfigurations.createdResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.downloadedResources.entryRetentionAge.set(twoDays)
        cacheConfigurations.releasedWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.snapshotWrappers.entryRetentionAge.set(twoDays)
        cacheConfigurations.buildCache.entryRetentionAge.set(twoDays)
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

    class FixedClock implements Clock {
        long time = System.currentTimeMillis()
        @Override
        long getCurrentTime() {
            return time
        }
    }
}
