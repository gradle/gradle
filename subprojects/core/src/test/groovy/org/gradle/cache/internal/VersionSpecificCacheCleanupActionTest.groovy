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

import org.gradle.internal.time.CountdownTimer
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

class VersionSpecificCacheCleanupActionTest extends Specification implements VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def userHomeDir = temporaryFolder.createDir("user-home")
    def currentCacheDir = createVersionSpecificCacheDir(currentVersion, NOT_USED_WITHIN_30_DAYS)

    @Subject def cleanupAction = new VersionSpecificCacheCleanupAction(cachesDir, 30, 7)

    def "cleans up unused version-specific cache directories"() {
        given:
        def ancientVersionWithoutMarkerFile = createVersionSpecificCacheDir(GradleVersion.version("0.0.1"), MISSING_MARKER_FILE)
        def oldestCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.2.3"), NOT_USED_WITHIN_30_DAYS)
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), USED_TODAY)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def newerCacheDir = createVersionSpecificCacheDir(currentVersion.getNextMajor(), NOT_USED_WITHIN_30_DAYS)

        when:
        cleanupAction.execute()

        then:
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
        cleanupAction.execute()

        then:
        sharedCacheDir.assertExists()
        dirWithUnparsableVersion.assertExists()
    }

    def "creates gc.properties file when it is missing"() {
        when:
        cleanupAction.execute()

        then:
        getGcFile(currentCacheDir).assertExists()
    }

    def "does not clean up when gc.properties has been touched in the last 24 hours"() {
        given:
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def gcFile = getGcFile(currentCacheDir).createFile().makeOlder()
        def originalLastModified = gcFile.lastModified()

        when:
        cleanupAction.execute()

        then:
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
        cleanupAction.execute()

        then:
        oldCacheDir.assertDoesNotExist()
        gcFile.lastModified() > originalLastModified
    }

    def "cleans up caches of snapshot versions not used within 7 days if there's a cache for a later release version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)
        def release = createVersionSpecificCacheDir(GradleVersion.version("4.8"), NOT_USED_WITHIN_7_DAYS)

        when:
        cleanupAction.execute()

        then:
        snapshot.assertDoesNotExist()
        release.assertExists()
    }

    def "cleans up caches of snapshot versions not used within 7 days if there's a cache for a later snapshot version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)
        def latestSnapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180507235951+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        cleanupAction.execute()

        then:
        snapshot.assertDoesNotExist()
        latestSnapshot.assertExists()
    }

    def "does not cleans up caches of snapshot versions not used within 7 days if there's no cache for a later version with same base version"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        cleanupAction.execute()

        then:
        snapshot.assertExists()
    }

    def "does not cleans up caches of recently used snapshot versions"() {
        given:
        def snapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180417000132+0000"), USED_TODAY)
        def latestSnapshot = createVersionSpecificCacheDir(GradleVersion.version("4.8-20180507235951+0000"), NOT_USED_WITHIN_7_DAYS)

        when:
        cleanupAction.execute()

        then:
        snapshot.assertExists()
        latestSnapshot.assertExists()
    }

    def "aborts cleanup when timeout has expired"() {
        given:
        def oldestCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.2.3"), NOT_USED_WITHIN_30_DAYS)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_30_DAYS)
        def timer = Mock(CountdownTimer)

        when:
        cleanupAction.performCleanup(timer)

        then:
        1 * timer.hasExpired() >> false
        1 * timer.hasExpired() >> true

        and:
        getGcFile(currentCacheDir).assertDoesNotExist()
        oldestCacheDir.assertDoesNotExist()
        oldCacheDir.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
