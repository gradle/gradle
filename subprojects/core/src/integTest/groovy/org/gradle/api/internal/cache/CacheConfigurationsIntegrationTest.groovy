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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class CacheConfigurationsIntegrationTest extends AbstractIntegrationSpec {
    private static final int MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS + 1
    private static final int MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS + 1
    private static final int MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES + 1
    private static final int MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1

    private static final String THIS_PROPERTY_FINAL_ERROR = "The value for this property is final and cannot be changed any further"
    private static final String REMOVE_UNUSED_ENTRIES_FINAL_ERROR = "The value for property 'removeUnusedEntriesAfterDays' is final and cannot be changed any further"

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "can configure caches via init script and query from settings script"() {
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}
                    snapshotWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}
                    downloadedResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}
                    createdResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}
                }
            }
        """
        settingsFile << """
            caches {
                assert releasedWrappers.removeUnusedEntriesAfterDays.get() == ${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}
                assert snapshotWrappers.removeUnusedEntriesAfterDays.get() == ${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}
                assert downloadedResources.removeUnusedEntriesAfterDays.get() == ${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}
                assert createdResources.removeUnusedEntriesAfterDays.get() == ${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}
            }
        """

        expect:
        succeeds("help")
    }

    def "cannot configure caches from settings script (#property)"() {
        settingsFile << """
            caches {
                ${modifyCacheConfiguration(property, value)}
            }
        """

        expect:
        fails("help")
        failureCauseContains(error)

        where:
        property                                           | error                             | value
        'cleanup'                                          | THIS_PROPERTY_FINAL_ERROR         | 'Cleanup.DISABLED'
        'releasedWrappers.removeUnusedEntriesAfterDays'    | REMOVE_UNUSED_ENTRIES_FINAL_ERROR | "${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}"
        'snapshotWrappers.removeUnusedEntriesAfterDays'    | REMOVE_UNUSED_ENTRIES_FINAL_ERROR | "${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}"
        'downloadedResources.removeUnusedEntriesAfterDays' | REMOVE_UNUSED_ENTRIES_FINAL_ERROR | "${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}"
        'createdResources.removeUnusedEntriesAfterDays'    | REMOVE_UNUSED_ENTRIES_FINAL_ERROR | "${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}"
    }

    static String modifyCacheConfiguration(String property, String value) {
        return """
            ${property} = ${value}
        """
    }
}
