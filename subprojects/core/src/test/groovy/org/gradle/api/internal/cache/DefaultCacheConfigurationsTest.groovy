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
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.internal.LegacyCacheCleanupEnablement
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.internal.time.TimestampSuppliers.daysAgo

class DefaultCacheConfigurationsTest extends Specification {
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Mock(LegacyCacheCleanupEnablement))

    def "cannot modify cache configurations via convenience method unless mutable"() {
        when:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.cleanup.set(Cleanup.DISABLED)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurationValues()

        and:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        def e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")
    }

    def "cannot modify cache configurations via property unless mutable (method: #method)"() {
        long firstValue = 2
        long secondValue = 1

        when:
        cacheConfigurations.createdResources.removeUnusedEntriesOlderThan."${method}"(firstValue)
        cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan."${method}"(firstValue)
        cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan."${method}"(firstValue)
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan."${method}"(firstValue)
        cacheConfigurations.cleanup."${method}"(Cleanup.DISABLED)
        cacheConfigurations.markingStrategy."${method}"(MarkingStrategy.NONE)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurationValues()

        and:
        cacheConfigurations.createdResources.removeUnusedEntriesOlderThan."${method}"(secondValue)

        then:
        def e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

        when:
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan."${method}"(secondValue)

        then:
        e = thrown(IllegalStateException)
        assertCannotConfigureErrorIsThrown(e, "removeUnusedEntriesOlderThan")

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

    def "suppliers reflect changes in property values"() {
        when:
        def createdResources = cacheConfigurations.createdResources.removeUnusedEntriesOlderThanAsSupplier
        def downloadedResources = cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThanAsSupplier
        def releasedWrappers = cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThanAsSupplier
        def snapshotWrappers = cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThanAsSupplier

        and:
        def twoDaysAgo = daysAgo(2).get()
        cacheConfigurations.createdResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)

        then:
        createdResources.get() == twoDaysAgo
        downloadedResources.get() == twoDaysAgo
        releasedWrappers.get() == twoDaysAgo
        snapshotWrappers.get() == twoDaysAgo
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
    }

    def "synchronized configurations reflect changes in property values"() {
        def mutableCacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations, Mock(LegacyCacheCleanupEnablement))

        when:
        cacheConfigurations.synchronize(mutableCacheConfigurations)

        and:
        def twoDaysAgo = daysAgo(2).get()
        cacheConfigurations.createdResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.markingStrategy.set(MarkingStrategy.NONE)

        then:
        mutableCacheConfigurations.createdResources.removeUnusedEntriesOlderThan.get() == twoDaysAgo
        mutableCacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan.get() == twoDaysAgo
        mutableCacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan.get() == twoDaysAgo
        mutableCacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan.get() == twoDaysAgo
        mutableCacheConfigurations.markingStrategy.get() == MarkingStrategy.NONE
    }

    def "will not require cleanup unless configured"() {
        when:
        def twoDaysAgo = daysAgo(2).get()
        cacheConfigurations.createdResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        cacheConfigurations.cleanup.set(Cleanup.ALWAYS)

        then:
        !cacheConfigurations.cleanupFrequency.get().requiresCleanup(CleanupFrequency.NEVER_CLEANED)

        when:
        cacheConfigurations.cleanupHasBeenConfigured = true

        then:
        cacheConfigurations.cleanupFrequency.get().requiresCleanup(CleanupFrequency.NEVER_CLEANED)
    }

    void assertCannotConfigureErrorIsThrown(Exception e, String name) {
        assert e.message.contains(String.format(DefaultCacheConfigurations.UNSAFE_MODIFICATION_ERROR, name))
    }
}
