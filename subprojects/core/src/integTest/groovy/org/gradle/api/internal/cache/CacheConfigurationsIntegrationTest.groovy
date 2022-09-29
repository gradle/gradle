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

    def "can configure caches via init script and query from settings script"() {
        requireOwnGradleUserHomeDir()
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    ${modifiedCacheConfigurations}
                }
            }
        """
        settingsFile << """
            caches {
                assert releasedWrappers.removeUnusedEntriesAfterDays.get() == ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS + 1}
                assert snapshotWrappers.removeUnusedEntriesAfterDays.get() == ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS + 1}
                assert downloadedResources.removeUnusedEntriesAfterDays.get() == ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES + 1}
                assert createdResources.removeUnusedEntriesAfterDays.get() == ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1}
            }
        """

        expect:
        succeeds("help")
    }

    def "cannot configure caches from settings script"() {
        settingsFile << """
            caches {
                ${modifiedCacheConfigurations}
            }
        """

        expect:
        fails("help")
        failureCauseContains("The value for property 'removeUnusedEntriesAfterDays' is final and cannot be changed any further")
    }

    static String getModifiedCacheConfigurations() {
        return """
                    releasedWrappers.removeUnusedEntriesAfterDays = ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS + 1}
                    snapshotWrappers.removeUnusedEntriesAfterDays = ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS + 1}
                    downloadedResources.removeUnusedEntriesAfterDays = ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES + 1}
                    createdResources.removeUnusedEntriesAfterDays = ${CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1}
        """
    }
}
