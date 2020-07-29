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

import org.gradle.cache.internal.VersionSpecificCacheCleanupFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_7_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.USED_TODAY

class ProjectCacheDirIntegrationTest extends AbstractIntegrationSpec implements VersionSpecificCacheCleanupFixture {

    def "cleans up unused version-specific cache directories from project directory"() {
        given:
        def oldButRecentlyUsedCacheDir = createVersionSpecificCacheDir(GradleVersion.version("1.4.5"), USED_TODAY)
        def oldCacheDir = createVersionSpecificCacheDir(GradleVersion.version("2.3.4"), NOT_USED_WITHIN_7_DAYS)
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_USED_WITHIN_7_DAYS)

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedCacheDir.assertExists()
        oldCacheDir.assertDoesNotExist()
        currentCacheDir.assertExists()
        getGcFile(currentCacheDir).assertExists()
    }

    @Override
    TestFile getCachesDir() {
        testDirectory.file(".gradle")
    }
}
