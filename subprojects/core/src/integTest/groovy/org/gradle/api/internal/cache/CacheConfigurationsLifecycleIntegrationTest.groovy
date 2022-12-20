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

import org.gradle.cache.internal.GradleUserHomeCleanupFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_30_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.USED_TODAY


class CacheConfigurationsLifecycleIntegrationTest extends AbstractIntegrationSpec implements GradleUserHomeCleanupFixture {
    def setup() {
        requireOwnGradleUserHomeDir()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }

    def "does not cleanup caches when initialization fails before cache configuration"() {
        withReleasedWrappersRetentionInDays(60)

        def oldButRecentlyUsedGradleDist = versionedDistDirs("1.4.5", USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs("2.3.4", NOT_USED_WITHIN_30_DAYS, "my-dist-2")

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_USED_WITHIN_30_DAYS)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        def initDir = gradleUserHomeDir.createDir('init.d')
        initDir.createFile('broken.gradle') << """
            throw new Exception('BOOM!')
        """

        when:
        fails("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        getGcFile(currentCacheDir).assertDoesNotExist()
    }
}
