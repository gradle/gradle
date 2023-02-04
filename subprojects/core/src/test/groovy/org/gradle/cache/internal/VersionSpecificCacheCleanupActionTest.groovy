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
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.MISSING_MARKER_FILE
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_30_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_7_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.USED_TODAY

import static org.gradle.internal.time.TimestampSuppliers.daysAgo

class VersionSpecificCacheCleanupActionTest extends Specification implements GradleUserHomeCleanupFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def userHomeDir = temporaryFolder.createDir("user-home")
    def currentCacheDir = createVersionSpecificCacheDir(currentVersion, NOT_USED_WITHIN_30_DAYS)
    def progressMonitor = Mock(CleanupProgressMonitor)
    def deleter = TestFiles.deleter()

    @Subject def cleanupAction = new VersionSpecificCacheCleanupAction(cachesDir, daysAgo(30), daysAgo(7), deleter, CleanupFrequency.DAILY)

    def "cleans up unused version-specific cache directories"() {
        given:
        def ancientVersionWithoutMarkerFile = createVersionSpecificCacheDir(GradleVersion.version("0.0.1"), MISSING_MARKER_FILE)
        def oldestCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.2.3"), NOT_USED_WITHIN_30_DAYS)
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), USED_TODAY)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def newerCacheDir = createVersionSpecificCacheDir(currentVersion.getNextMajorVersion(), NOT_USED_WITHIN_30_DAYS)

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        4 * progressMonitor.incrementSkipped()
        2 * progressMonitor.incrementDeleted()
        ancientVersionWithoutMarkerFile.assertExists()
        oldestCacheDir.assertDoesNotExist()
        oldButRecentlyUsedCacheDir.assertExists()
        oldCacheDir.assertDoesNotExist()
        currentCacheDir.assertExists()
        newerCacheDir.assertExists()
    }

    def "ignores directories that are not version-specific caches"() {
        given:
        def sharedCacheDir = createCacheSubDir("some-cache-42")
        def dirWithUnparsableVersion = createCacheSubDir("42 foo")

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        1 * progressMonitor.incrementSkipped()
        0 * progressMonitor.incrementDeleted()
        sharedCacheDir.assertExists()
        dirWithUnparsableVersion.assertExists()
    }

    def "creates gc.properties file when it is missing"() {
        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        getGcFile(currentCacheDir).assertExists()
    }

    def "does not clean up when gc.properties has been touched in the last 24 hours"() {
        given:
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def gcFile = getGcFile(currentCacheDir).createFile().makeOlder()
        def originalLastModified = gcFile.lastModified()

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        !cleanedUp
        0 * progressMonitor._
        oldCacheDir.assertExists()
        gcFile.lastModified() == originalLastModified
    }

    def "cleans up when gc.properties has been touched in the last 24 hours"() {
        given:
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def gcFile = getGcFile(currentCacheDir).createFile()
        def originalLastModified = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)
        gcFile.lastModified = originalLastModified

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        1 * progressMonitor.incrementSkipped()
        1 * progressMonitor.incrementDeleted()
        oldCacheDir.assertDoesNotExist()
        gcFile.lastModified() > originalLastModified
    }

    def "deletes caches of snapshot versions not used within 7 days if there's a cache for a later release version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)
        def release = createVersionSpecificCacheDir(GradleVersion.version("4.8"), NOT_USED_WITHIN_7_DAYS)

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementSkipped()
        1 * progressMonitor.incrementDeleted()
        snapshot.assertDoesNotExist()
        release.assertExists()
    }

    def "deletes caches of snapshot versions not used within 7 days if there's a cache for a later snapshot version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)
        def latestSnapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180507235951+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementSkipped()
        1 * progressMonitor.incrementDeleted()
        snapshot.assertDoesNotExist()
        latestSnapshot.assertExists()
    }

    def "does not delete caches of snapshot versions not used within 7 days if there's no cache for a later version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        2 * progressMonitor.incrementSkipped()
        0 * progressMonitor.incrementDeleted()
        snapshot.assertExists()
    }

    def "does not delete caches of recently used snapshot versions"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), USED_TODAY)
        def latestSnapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180507235951+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        def cleanedUp = cleanupAction.execute(progressMonitor)

        then:
        cleanedUp
        3 * progressMonitor.incrementSkipped()
        0 * progressMonitor.incrementDeleted()
        snapshot.assertExists()
        latestSnapshot.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
