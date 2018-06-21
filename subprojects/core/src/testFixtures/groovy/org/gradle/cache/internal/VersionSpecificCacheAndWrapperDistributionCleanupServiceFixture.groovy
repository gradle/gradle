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
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupService.MARKER_FILE_PATH
import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupService.WRAPPER_DISTRIBUTION_FILE_PATH
import static org.gradle.cache.internal.VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture.MarkerFileType.MISSING_MARKER_FILE

trait VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture {

    TestFile createVersionSpecificCacheDir(GradleVersion version, MarkerFileType type = MISSING_MARKER_FILE) {
        return createCacheSubDir(version.version, type)
    }

    TestFile createCacheSubDir(String name, MarkerFileType type = MISSING_MARKER_FILE) {
        def cachesDir = getGradleUserHomeDir().file(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME).createDir()
        def versionDir = cachesDir.file(name).createDir()
        def markerFile = versionDir.file(MARKER_FILE_PATH)
        type.process(markerFile)
        return versionDir
    }

    TestFile createDistributionDir(GradleVersion version, String distributionType) {
        def cachesDir = getGradleUserHomeDir().file(WRAPPER_DISTRIBUTION_FILE_PATH).createDir()
        def versionDir = cachesDir.file("gradle-${version.version}-$distributionType").createDir()
        return versionDir
    }

    TestFile getGcFile(TestFile currentCacheDir) {
        currentCacheDir.file("gc.properties")
    }

    abstract TestFile getGradleUserHomeDir()

    static enum MarkerFileType {

        USED_TODAY {
            @Override
            void process(TestFile markerFile) {
                markerFile.createFile()
            }
        },

        NOT_USED_WITHIN_30_DAYS {
            @Override
            void process(TestFile markerFile) {
                markerFile.createFile()
                markerFile.lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)
            }
        },

        NOT_USED_WITHIN_7_DAYS {
            @Override
            void process(TestFile markerFile) {
                markerFile.createFile()
                markerFile.lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)
            }
        },

        MISSING_MARKER_FILE

        void process(TestFile markerFile) {}
    }
}
