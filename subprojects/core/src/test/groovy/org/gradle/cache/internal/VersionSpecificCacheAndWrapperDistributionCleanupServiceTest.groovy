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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.MISSING_MARKER_FILE
import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.NOT_RECENTLY_USED
import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.RECENTLY_USED

class VersionSpecificCacheAndWrapperDistributionCleanupServiceTest extends Specification implements VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    GradleVersion currentVersion = GradleVersion.version("3.14")
    TestFile userHomeDir = temporaryFolder.file("user-home").createDir()

    @Subject def cleanupService = new VersionSpecificCacheAndWrapperDistributionCleanupService(currentVersion, userHomeDir)

    def "cleans up unused version-specific cache directories"() {
        given:
        def ancientVersionWithoutMarkerFile = createVersionSpecificCacheDir(GradleVersion.version("0.0.1"), MISSING_MARKER_FILE)
        def oldestCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.2.3"), NOT_RECENTLY_USED)
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), RECENTLY_USED)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_RECENTLY_USED)
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_RECENTLY_USED)
        def newerCacheDir = createVersionSpecificCacheDir(GradleVersion.current().getNextMajor(), NOT_RECENTLY_USED)

        when:
        cleanupService.stop()

        then:
        ancientVersionWithoutMarkerFile.assertExists()
        oldestCacheDir.assertDoesNotExist()
        oldButRecentlyUsedCacheDir.assertExists()
        oldCacheDir.assertDoesNotExist()
        currentCacheDir.assertExists()
        newerCacheDir.assertExists()
    }

    def "deletes distributions for unused versions"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_RECENTLY_USED)
        def oldAllDist = createDistributionDir(oldVersion, "all")
        def oldBinDist = createDistributionDir(oldVersion, "bin")
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_RECENTLY_USED)
        def currentAllDist = createDistributionDir(GradleVersion.current(), "all")
        def currentBinDist = createDistributionDir(GradleVersion.current(), "bin")

        when:
        cleanupService.stop()

        then:
        oldCacheDir.assertDoesNotExist()
        oldAllDist.assertDoesNotExist()
        oldBinDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentAllDist.assertExists()
        currentBinDist.assertExists()
    }

    def "ignores directories that are not version-specific caches"() {
        given:
        def sharedCacheDir = createCacheSubDir("some-cache-42")
        def dirWithUnparsableVersion = createCacheSubDir("42 foo")

        when:
        cleanupService.stop()

        then:
        sharedCacheDir.assertExists()
        dirWithUnparsableVersion.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
