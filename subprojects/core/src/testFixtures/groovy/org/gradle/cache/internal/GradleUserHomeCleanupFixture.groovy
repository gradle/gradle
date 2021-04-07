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
import org.gradle.util.internal.DefaultGradleVersion

import java.util.concurrent.TimeUnit

import static org.gradle.cache.internal.WrapperDistributionCleanupAction.WRAPPER_DISTRIBUTION_FILE_PATH

trait GradleUserHomeCleanupFixture implements VersionSpecificCacheCleanupFixture {

    private static final BiAction<GradleVersion, File> DEFAULT_JAR_WRITER = { version, jarFile ->
        jarFile << JarUtils.jarWithContents((DefaultGradleVersion.RESOURCE_NAME.substring(1)): "${DefaultGradleVersion.VERSION_NUMBER_PROPERTY}: ${version.version}")
    }
    static final String DEFAULT_JAR_PREFIX = 'gradle-base-services'

    @Override
    TestFile getCachesDir() {
        gradleUserHomeDir.file(DefaultCacheScopeMapping.GLOBAL_CACHE_DIR_NAME)
    }

    TestFile createDistributionChecksumDir(GradleVersion version, String jarPrefix = DEFAULT_JAR_PREFIX, long lastModificationTime = twoDaysAgo()) {
        createCustomDistributionChecksumDir("gradle-${version.version}-all", version, jarPrefix, lastModificationTime)
    }

    TestFile createCustomDistributionChecksumDir(String parentDirName, GradleVersion version, String jarPrefix = DEFAULT_JAR_PREFIX, long lastModificationTime = twoDaysAgo(), BiAction<GradleVersion, File> jarWriter = DEFAULT_JAR_WRITER) {
        def checksumDir = distsDir.file(parentDirName).createDir(UUID.randomUUID())
        def libDir = checksumDir.file("gradle-${version.baseVersion.version}", "lib").createDir()
        def jarFile = libDir.file("$jarPrefix-${version.baseVersion.version}.jar")
        jarWriter.execute(version, jarFile)
        checksumDir.lastModified = lastModificationTime
        return checksumDir
    }

    long twoDaysAgo() {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
    }

    TestFile getDistsDir() {
        gradleUserHomeDir.file(WRAPPER_DISTRIBUTION_FILE_PATH)
    }

    abstract TestFile getGradleUserHomeDir()

}
