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
import org.gradle.internal.time.CountdownTimer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

@CleanupTestDirectory
class UnusedVersionsCacheCleanupTest extends Specification {

    static final String CACHE_NAME = "cache"

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def gradleVersionProvider = Stub(GradleVersionProvider)

    @Unroll
    def "deletes unused cache directories for mapping #mapping, Gradle versions #gradleVersions and existing cache versions #existingCacheVersions"() {
        given:
        existingCacheVersions.each { version ->
            versionDir(version)
                .createDir()
                .createFile("test.txt").text = "foo"
        }
        def cacheVersionMapping = toCacheVersionMapping(mapping)
        def cleanableStore = Stub(CleanableStore) {
            getBaseDir() >> versionDir(cacheVersionMapping.latestVersion)
            getReservedCacheFiles() >> []
            getDisplayName() >> CACHE_NAME
        }

        when:
        UnusedVersionsCacheCleanup.create(CACHE_NAME, cacheVersionMapping, gradleVersionProvider)
            .clean(cleanableStore, Stub(CountdownTimer))

        then:
        gradleVersionProvider.getRecentlyUsedVersions() >> (gradleVersions.collect { GradleVersion.version(it) } as SortedSet)
        for (version in (existingCacheVersions - expectedDeletedVersions)) {
            versionDir(version).assertExists()
        }
        for (version in expectedDeletedVersions) {
            versionDir(version).assertDoesNotExist()
        }

        where:
        mapping                              | gradleVersions        | existingCacheVersions || expectedDeletedVersions
        [[1, "4.1"]]                         | []                    | [1]                   || []
        [[1, "4.1"], [2, "4.3"], [5, "4.8"]] | ["3.9", "4.2", "4.9"] | [1, 2, 5, 6]          || [2]
        [[1, "4.1"], [2, "4.3"], [5, "4.8"]] | []                    | [1, 2, 5, 6]          || [1, 2]
    }

    TestFile versionDir(int version) {
        temporaryFolder.file("$CACHE_NAME-$version")
    }

    CacheVersionMapping toCacheVersionMapping(List<List<?>> mapping) {
        assert mapping[0][0] == 1
        def builder = CacheVersionMapping.introducedIn(mapping[0][1])
        mapping.tail().each {
            builder.changedTo(it[0], it[1])
        }
        return builder.build()
    }
}
