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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.NOT_USED_WITHIN_30_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.USED_TODAY

class VersionSpecificCacheAndWrapperDistributionCleanupServiceIntegrationTest extends AbstractIntegrationSpec implements VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture {

    def "cleans up unused version-specific cache directories and corresponding distributions"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions

        and:
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), USED_TODAY)
        def oldNotRecentlyUsedVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldNotRecentlyUsedVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionDir(oldNotRecentlyUsedVersion, "bin")
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_USED_WITHIN_30_DAYS)
        def currentDist = createDistributionDir(GradleVersion.current(), "all")

        when:
        succeeds("tasks")

        then:
        oldButRecentlyUsedCacheDir.assertExists()
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
        getGcFile(currentCacheDir).assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
