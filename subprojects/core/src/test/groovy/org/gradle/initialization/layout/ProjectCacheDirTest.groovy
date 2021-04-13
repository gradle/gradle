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

package org.gradle.initialization.layout

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.internal.VersionSpecificCacheCleanupFixture
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.MISSING_MARKER_FILE
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_7_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.USED_TODAY

class ProjectCacheDirTest extends Specification implements VersionSpecificCacheCleanupFixture {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def cacheDir = temporaryFolder.createDir(".gradle")
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def progressLogger = Mock(ProgressLogger)
    def deleter = TestFiles.deleter()

    @Subject def projectCacheDir = new ProjectCacheDir(cacheDir, progressLoggerFactory, deleter)

    def "cleans up unused version-specific cache directories"() {
        given:
        def ancientVersionWithoutMarkerFile = createVersionSpecificCacheDir(GradleVersion.version("0.0.1"), MISSING_MARKER_FILE)
        def oldestCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.2.3"), NOT_USED_WITHIN_7_DAYS)
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), USED_TODAY)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_7_DAYS)
        def currentCacheDir = createVersionSpecificCacheDir(currentVersion, NOT_USED_WITHIN_7_DAYS)
        def newerCacheDir = createVersionSpecificCacheDir(currentVersion.getNextMajorVersion(), NOT_USED_WITHIN_7_DAYS)

        when:
        projectCacheDir.stop()

        then:
        1 * progressLoggerFactory.newOperation(ProjectCacheDir.class) >> progressLogger
        1 * progressLogger.start(_, _) >> progressLogger
        6 * progressLogger.progress(_)
        1 * progressLogger.completed()
        ancientVersionWithoutMarkerFile.assertExists()
        oldestCacheDir.assertDoesNotExist()
        oldButRecentlyUsedCacheDir.assertExists()
        oldCacheDir.assertDoesNotExist()
        currentCacheDir.assertExists()
        newerCacheDir.assertExists()
    }

    @Override
    TestFile getCachesDir() {
        return cacheDir
    }
}
