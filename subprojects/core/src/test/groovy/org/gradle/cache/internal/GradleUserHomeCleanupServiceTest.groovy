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

import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_30_DAYS

class GradleUserHomeCleanupServiceTest extends Specification implements GradleUserHomeCleanupFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def userHomeDir = temporaryFolder.createDir("user-home")
    def currentCacheDir = createVersionSpecificCacheDir(currentVersion, NOT_USED_WITHIN_30_DAYS)

    def userHomeDirProvider = Stub(GradleUserHomeDirProvider) {
        getGradleUserHomeDirectory() >> userHomeDir
    }
    def cacheScopeMapping = new DefaultCacheScopeMapping(userHomeDir, null, GradleVersion.current())
    def usedGradleVersions = Stub(UsedGradleVersions) {
        getUsedGradleVersions() >> ([] as SortedSet)
    }
    def progressLoggerFactory = Stub(ProgressLoggerFactory)

    @Subject def cleanupService = new GradleUserHomeCleanupService(
        TestFiles.deleter(),
        userHomeDirProvider,
        cacheScopeMapping,
        usedGradleVersions,
        progressLoggerFactory
    )

    def "cleans up unused version-specific cache directories and deletes distributions for unused versions"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.stop()

        then:
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "skips cleanup of version-specific caches and distributions if gc.properties has recently been changed"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile

        when:
        getGcFile(currentCacheDir).touch()
        cleanupService.stop()

        then:
        oldCacheDir.assertExists()
        oldDist.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
