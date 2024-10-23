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

package org.gradle.integtests.resolve.caching


import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.cache.internal.GradleUserHomeCleanupFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile

class ArtifactCacheUnusedVersionCleanupIntegrationTest extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture, GradleUserHomeCleanupFixture {

    TestFile oldModulesDir
    TestFile oldMetadataDir
    TestFile currentModulesDir
    TestFile currentModulesMetadataDir

    def setup() {
        requireOwnGradleUserHomeDir("messes with caches")
        oldModulesDir = userHomeCacheDir.createDir("${CacheLayout.MODULES.name}-1")
        oldMetadataDir = userHomeCacheDir.file(CacheLayout.MODULES.key).createDir("${CacheLayout.META_DATA.name}-2.56")
        currentModulesDir = userHomeCacheDir.file(CacheLayout.MODULES.key).createDir()
        currentModulesMetadataDir = currentModulesDir.file(CacheLayout.META_DATA.key).createDir()
        gcFile.createFile()
    }

    def "cleans up unused versions of caches when latest cache requires cleanup"() {
        given:
        gcFile.lastModified = daysAgo(2)

        when:
        succeeds("help")

        then:
        oldModulesDir.assertDoesNotExist()
        oldMetadataDir.assertDoesNotExist()
        currentModulesDir.assertExists()
        currentModulesMetadataDir.assertExists()
    }

    def "cleans up unused versions of caches on cleanup always"() {
        given:
        alwaysCleanupCaches()

        when:
        succeeds("help")

        then:
        oldModulesDir.assertDoesNotExist()
        oldMetadataDir.assertDoesNotExist()
        currentModulesDir.assertExists()
        currentModulesMetadataDir.assertExists()
    }

    def "does not cleanup unused versions of caches when cleanup disabled"(CleanupMethod method) {
        given:
        gcFile.lastModified = daysAgo(2)
        disableCacheCleanup(method)

        when:
        method.maybeExpectDeprecationWarning(executer)
        succeeds("help")

        then:
        oldModulesDir.assertExists()
        oldMetadataDir.assertExists()
        currentModulesDir.assertExists()
        currentModulesMetadataDir.assertExists()

        where:
        method << CleanupMethod.values()
    }

    TestFile getGcFile() {
        return currentModulesDir.file("gc.properties")
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
