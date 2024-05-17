/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class UnusedVersionsCacheCleanupTest extends Specification {

    static final String CACHE_NAME = "cache"

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def usedGradleVersions = Stub(UsedGradleVersions)
    def progressMonitor = Mock(CleanupProgressMonitor)

    def "deletes unused cache directories for mapping #mapping, Gradle versions #gradleVersions and existing cache versions #existingCacheVersions"() {
        given:
        def parentCacheVersion = CacheVersion.parse(parentVersion)
        existingCacheVersions.each { version ->
            versionDir(parentCacheVersion.append(version))
                .createDir()
                .createFile("test.txt").text = "foo"
        }
        def cacheVersionMapping = toCacheVersionMapping(mapping, parentCacheVersion)
        def cleanableStore = Stub(CleanableStore) {
            getBaseDir() >> versionDir(cacheVersionMapping.latestVersion)
            getReservedCacheFiles() >> []
            getDisplayName() >> CACHE_NAME
        }
        def expectedRemainingVersions = existingCacheVersions - expectedDeletedVersions
        def numSkipped = Math.max(0, expectedRemainingVersions.size() - 1) // -1 because current version is not checked
        def numDeleted = expectedDeletedVersions.size()

        when:
        UnusedVersionsCacheCleanup.create(CACHE_NAME, cacheVersionMapping, usedGradleVersions)
            .clean(cleanableStore, progressMonitor)

        then:
        usedGradleVersions.getUsedGradleVersions() >> (gradleVersions.collect { GradleVersion.version(it) } as SortedSet)
        numSkipped * progressMonitor.incrementSkipped()
        numDeleted * progressMonitor.incrementDeleted()
        for (version in expectedRemainingVersions) {
            versionDir(parentCacheVersion.append(version)).assertExists()
        }
        for (version in expectedDeletedVersions) {
            versionDir(parentCacheVersion.append(version)).assertDoesNotExist()
        }

        where:
        mapping                              | gradleVersions        | parentVersion | existingCacheVersions || expectedDeletedVersions
        [[1, "4.1"]]                         | []                    | ""            | [1]                   || []
        [[1, "4.1"], [2, "4.3"], [5, "4.8"]] | ["3.9", "4.2", "4.9"] | "42"          | [1, 2, 5, 6]          || [2]
        [[1, "4.1"], [2, "4.3"], [5, "4.8"]] | []                    | "23"          | [1, 2, 5, 6]          || [1, 2]
    }

    TestFile versionDir(CacheVersion version) {
        temporaryFolder.file("$CACHE_NAME-$version")
    }

    CacheVersionMapping toCacheVersionMapping(List<List<?>> mapping, CacheVersion parentCacheVersion) {
        assert mapping[0][0] == 1
        def builder = CacheVersionMapping.introducedIn(mapping[0][1])
        mapping.tail().each {
            builder.changedTo(it[0], it[1])
        }
        return builder.build(parentCacheVersion)
    }
}
