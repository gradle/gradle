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

import java.util.concurrent.TimeUnit

class CacheConfigurationsIntegrationTest extends AbstractIntegrationSpec {
    private static final int MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS + 1
    private static final int MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS + 1
    private static final int MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES + 1
    private static final int MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1
    private static final int MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES + 1

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "can configure caches via init script and query from settings script"() {
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    markingStrategy = MarkingStrategy.NONE
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}
                    snapshotWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}
                    downloadedResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}
                    createdResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}
                    buildCache.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES}
                }
            }
        """
        settingsFile << """
            caches {
                assert markingStrategy.get() == MarkingStrategy.NONE
                releasedWrappers { ${assertValueIsSameInDays(MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS)} }
                snapshotWrappers { ${assertValueIsSameInDays(MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS)} }
                downloadedResources { ${assertValueIsSameInDays(MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES)} }
                createdResources { ${assertValueIsSameInDays(MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES)} }
                buildCache { ${assertValueIsSameInDays(MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES)} }
            }
        """

        expect:
        succeeds("help")
    }

    def "cache retention timestamp is recalculated for each build execution"() {
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    markingStrategy = MarkingStrategy.NONE
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}
                    snapshotWrappers.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}
                    downloadedResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}
                    createdResources.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}
                    buildCache.removeUnusedEntriesAfterDays = ${MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES}
                }
            }
        """
        buildFile << """
            task printCacheTimestamps {
                doLast {
                    def caches = services.get(CacheConfigurations)
                    caches.with {
                        println "cacheTimestamps : {" + releasedWrappers.entryRetentionTimestampSupplier.get() +
                                                  "," + snapshotWrappers.entryRetentionTimestampSupplier.get() +
                                                  "," + downloadedResources.entryRetentionTimestampSupplier.get() +
                                                  "," + createdResources.entryRetentionTimestampSupplier.get() +
                                                  "," + buildCache.entryRetentionTimestampSupplier.get() + "}"
                    }
                }
            }
        """

        when:
        succeeds("printCacheTimestamps")
        def firstRunTimestamps = result.getOutputLineThatContains("cacheTimestamps : ")

        then:
        succeeds("printCacheTimestamps")
        def secondRunTimestamps = result.getOutputLineThatContains("cacheTimestamps : ")
        secondRunTimestamps != firstRunTimestamps
    }

    def "can configure caches to a custom entry retention"() {
        def initDir = new File(executer.gradleUserHomeDir, "init.d")
        initDir.mkdirs()

        def releasedDistTimestamp = TimeUnit.DAYS.toMillis(MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS)
        def snapshotDistTimestamp = TimeUnit.DAYS.toMillis(MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS)
        def downloadedResourcesTimestamp = TimeUnit.DAYS.toMillis(MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES)
        def createdResourcesTimestamp = TimeUnit.DAYS.toMillis(MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES)
        def buildCacheResourcesTimestamp = TimeUnit.DAYS.toMillis(MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES)

        new File(initDir, "cache-settings.gradle") << """
            import java.util.function.Supplier

            beforeSettings { settings ->
                settings.caches {
                    markingStrategy = MarkingStrategy.NONE
                    cleanup = Cleanup.DISABLED
                    releasedWrappers.entryRetentionMillis = ${releasedDistTimestamp}L
                    snapshotWrappers.entryRetentionMillis = ${snapshotDistTimestamp}L
                    downloadedResources.entryRetentionMillis = ${downloadedResourcesTimestamp}L
                    createdResources.entryRetentionMillis = ${createdResourcesTimestamp}L
                    buildCache.entryRetentionMillis = ${buildCacheResourcesTimestamp}L
                }
            }
        """
        settingsFile << """
            caches {
                assert markingStrategy.get() == MarkingStrategy.NONE
                assert releasedWrappers.entryRetentionMillis.get() == ${releasedDistTimestamp}
                assert snapshotWrappers.entryRetentionMillis.get() == ${snapshotDistTimestamp}
                assert downloadedResources.entryRetentionMillis.get() == ${downloadedResourcesTimestamp}
                assert createdResources.entryRetentionMillis.get() == ${createdResourcesTimestamp}
                assert buildCache.entryRetentionMillis.get() == ${buildCacheResourcesTimestamp}
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
        failureCauseContains(String.format(DefaultCacheConfigurations.UNSAFE_MODIFICATION_ERROR, errorProperty))

        where:
        property                                           | errorProperty          | value
        'markingStrategy'                                  | 'markingStrategy'      | 'MarkingStrategy.NONE'
        'cleanup'                                          | 'cleanup'              | 'Cleanup.DISABLED'
        'releasedWrappers.removeUnusedEntriesAfterDays'    | 'entryRetentionMillis' | "${MODIFIED_AGE_IN_DAYS_FOR_RELEASED_DISTS}"
        'snapshotWrappers.removeUnusedEntriesAfterDays'    | 'entryRetentionMillis' | "${MODIFIED_AGE_IN_DAY_FOR_SNAPSHOT_DISTS}"
        'downloadedResources.removeUnusedEntriesAfterDays' | 'entryRetentionMillis' | "${MODIFIED_AGE_IN_DAYS_FOR_DOWNLOADED_CACHE_ENTRIES}"
        'createdResources.removeUnusedEntriesAfterDays'    | 'entryRetentionMillis' | "${MODIFIED_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES}"
        'buildCache.removeUnusedEntriesAfterDays'          | 'entryRetentionMillis' | "${MODIFIED_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES}"
    }

    static String modifyCacheConfiguration(String property, String value) {
        return """
            ${property} = ${value}
        """
    }

    static String assertValueIsSameInDays(int configuredDaysAgo) {
        return """
            def timestamp = entryRetentionMillis.get()
            def daysAgo = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(timestamp)
            assert daysAgo == ${configuredDaysAgo}
        """
    }
}
