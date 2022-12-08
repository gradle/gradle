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
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.internal.time.TimestampSuppliers.daysAgo

class DefaultCacheConfigurationsTest extends Specification {
    private static final String CANNOT_CONFIGURE_MESSAGE = "You can only configure the property '%s' in an init script, preferably stored in the init.d directory inside the Gradle user home directory."

    def cacheConfigurations = TestUtil.objectFactory().newInstance(DefaultCacheConfigurations.class)

    def "cannot modify cache configurations via convenience method unless mutable"() {
        when:
        cacheConfigurations.withMutableValues {
            cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(2)
            cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(2)
            cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(2)
            cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(2)
            cacheConfigurations.cleanup.set(Cleanup.DISABLED)
        }

        then:
        noExceptionThrown()

        when:
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
        cacheConfigurations.withMutableValues {
            cacheConfigurations.createdResources.removeUnusedEntriesOlderThan."${method}"(firstValue)
            cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan."${method}"(firstValue)
            cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan."${method}"(firstValue)
            cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan."${method}"(firstValue)
            cacheConfigurations.cleanup."${method}"(Cleanup.DISABLED)
        }

        then:
        noExceptionThrown()

        when:
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
        cacheConfigurations.withMutableValues {
            cacheConfigurations.createdResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
            cacheConfigurations.downloadedResources.removeUnusedEntriesOlderThan.set(twoDaysAgo)
            cacheConfigurations.releasedWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
            cacheConfigurations.snapshotWrappers.removeUnusedEntriesOlderThan.set(twoDaysAgo)
        }

        then:
        createdResources.get() == twoDaysAgo
        downloadedResources.get() == twoDaysAgo
        releasedWrappers.get() == twoDaysAgo
        snapshotWrappers.get() == twoDaysAgo
    }

    def "cannot set values in days to less than one"() {
        when:
        cacheConfigurations.withMutableValues {
            cacheConfigurations.createdResources.setRemoveUnusedEntriesAfterDays(0)
        }

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.withMutableValues {
            cacheConfigurations.downloadedResources.setRemoveUnusedEntriesAfterDays(0)
        }

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.withMutableValues {
            cacheConfigurations.releasedWrappers.setRemoveUnusedEntriesAfterDays(0)
        }

        then:
        thrown(IllegalArgumentException)

        when:
        cacheConfigurations.withMutableValues {
            cacheConfigurations.snapshotWrappers.setRemoveUnusedEntriesAfterDays(0)
        }

        then:
        thrown(IllegalArgumentException)
    }

    void assertCannotConfigureErrorIsThrown(Exception e, String name) {
        assert e.message.contains(String.format(CANNOT_CONFIGURE_MESSAGE, name))
    }
}
