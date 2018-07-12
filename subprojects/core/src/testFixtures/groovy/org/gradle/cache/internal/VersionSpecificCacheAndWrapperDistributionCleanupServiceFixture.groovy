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

import org.gradle.internal.BiAction
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.JarUtils

import static org.gradle.cache.internal.WrapperDistributionCleanupAction.WRAPPER_DISTRIBUTION_FILE_PATH

trait VersionSpecificCacheAndWrapperDistributionCleanupServiceFixture implements VersionSpecificCacheCleanupFixture {

    private static final BiAction<GradleVersion, File> DEFAULT_JAR_WRITER = { version, jarFile ->
        jarFile << JarUtils.jarWithContents((GradleVersion.RESOURCE_NAME.substring(1)): "${GradleVersion.VERSION_NUMBER_PROPERTY}: ${version.version}")
    }

    @Override
    TestFile getCachesDir() {
        gradleUserHomeDir.file(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)
    }

    TestFile createDistributionChecksumDir(GradleVersion version) {
        createCustomDistributionChecksumDir("gradle-${version.version}-all", version)
    }

    TestFile createCustomDistributionChecksumDir(String parentDirName, GradleVersion version, BiAction<GradleVersion, File> jarWriter = DEFAULT_JAR_WRITER) {
        def checksumDir = distsDir.file(parentDirName).createDir(UUID.randomUUID())
        def libDir = checksumDir.file("gradle-${version.baseVersion.version}", "lib").createDir()
        def jarFile = libDir.file("gradle-base-services-${version.baseVersion.version}.jar")
        jarWriter.execute(version, jarFile)
        return checksumDir
    }

    TestFile getDistsDir() {
        gradleUserHomeDir.file(WRAPPER_DISTRIBUTION_FILE_PATH)
    }

    abstract TestFile getGradleUserHomeDir()

}
