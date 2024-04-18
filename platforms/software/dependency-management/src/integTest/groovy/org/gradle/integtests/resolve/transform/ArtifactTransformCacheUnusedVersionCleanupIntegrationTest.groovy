/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.cache.internal.GradleUserHomeCleanupFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile

class ArtifactTransformCacheUnusedVersionCleanupIntegrationTest extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture, GradleUserHomeCleanupFixture {

    TestFile transforms3Dir
    TestFile transforms4Dir
    TestFile currentTransformsDir
    TestFile currentModulesDir


    def setup() {
        requireOwnGradleUserHomeDir("messes with caches")
        transforms3Dir = userHomeCacheDir.createDir("${CacheLayout.TRANSFORMS.name}-3")
        transforms4Dir = userHomeCacheDir.createDir("${CacheLayout.TRANSFORMS.name}-4")
        currentTransformsDir = gradleVersionedCacheDir.file(CacheLayout.TRANSFORMS.name).createDir()
        currentModulesDir = userHomeCacheDir.file(CacheLayout.MODULES.key).createDir()
        modulesGcFile.createFile()
    }

    def "cleans up all old unused versions of transforms-X when current modules requires cleanup"() {
        given:
        modulesGcFile.lastModified = daysAgo(2)

        when:
        succeeds("help")

        then:
        transforms3Dir.assertDoesNotExist()
        transforms4Dir.assertDoesNotExist()
        currentTransformsDir.assertExists()
        currentModulesDir.assertExists()
    }

    def "retains used versions of transforms-X when current modules requires cleanup"() {
        given:
        userHomeCacheDir.file("8.7").file("gc.properties").createFile()
        modulesGcFile.lastModified = daysAgo(2)

        when:
        succeeds("help")

        then:
        transforms3Dir.assertDoesNotExist()
        transforms4Dir.assertExists() // Transforms-4 is used by Gradle 8.7
        currentTransformsDir.assertExists()
        currentModulesDir.assertExists()
    }

    def "cleans up old unused versions of transforms-X when cleanup always configured"() {
        given:
        alwaysCleanupCaches()

        when:
        succeeds("help")

        then:
        transforms3Dir.assertDoesNotExist()
        transforms4Dir.assertDoesNotExist()
        currentTransformsDir.assertExists()
        currentModulesDir.assertExists()
    }

    def "does not cleans up old unused versions of transforms-X when cleanup disabled"(CleanupMethod method) {
        given:
        modulesGcFile.lastModified = daysAgo(2)
        disableCacheCleanup(method)

        when:
        method.maybeExpectDeprecationWarning(executer)
        succeeds("help")

        then:
        transforms3Dir.assertExists()
        transforms4Dir.assertExists()
        currentTransformsDir.assertExists()
        currentModulesDir.assertExists()

        where:
        method << CleanupMethod.values()
    }

    TestFile getModulesGcFile() {
        return currentModulesDir.file("gc.properties")
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
