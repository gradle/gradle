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
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.internal.time.TimestampSuppliers.daysAgo

class DefaultCacheConfigurationsTest extends Specification {
    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class, Stub(GradleUserHomeDirProvider))

    def "cannot modify cache configurations once finalized"() {
        when:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(2)
        cacheConfigurations.cleanup.set(Cleanup.DISABLED)

        then:
        noExceptionThrown()

        when:
        cacheConfigurations.finalizeConfigurations()

        and:
        cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(1)

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(1)

        then:
        thrown(IllegalStateException)

        when:
        cacheConfigurations.cleanup.set(Cleanup.DEFAULT)

        then:
        thrown(IllegalStateException)
    }

    def "suppliers reflect changes in property values"() {
        when:
        def createdResources = cacheConfigurations.createdResources.removeUnusedEntriesAfterAsSupplier
        def downloadedResources = cacheConfigurations.downloadedResources.removeUnusedEntriesAfterAsSupplier
        def releasedWrappers = cacheConfigurations.releasedWrappers.removeUnusedEntriesAfterAsSupplier
        def snapshotWrappers = cacheConfigurations.snapshotWrappers.removeUnusedEntriesAfterAsSupplier

        and:
        def twoDaysAgo = daysAgo(2).get()
        cacheConfigurations.createdResources.removeUnusedEntriesAfter.set(twoDaysAgo)
        cacheConfigurations.downloadedResources.removeUnusedEntriesAfter.set(twoDaysAgo)
        cacheConfigurations.releasedWrappers.removeUnusedEntriesAfter.set(twoDaysAgo)
        cacheConfigurations.snapshotWrappers.removeUnusedEntriesAfter.set(twoDaysAgo)

        then:
        createdResources.get() == twoDaysAgo
        downloadedResources.get() == twoDaysAgo
        releasedWrappers.get() == twoDaysAgo
        snapshotWrappers.get() == twoDaysAgo
    }
}
