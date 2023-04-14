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

import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.internal.BiAction
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.JarUtils
import org.gradle.util.internal.DefaultGradleVersion

import java.text.SimpleDateFormat
import java.time.Instant
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

    void disableCacheCleanupViaProperty() {
        gradleUserHomeDir.mkdirs()
        new File(gradleUserHomeDir, 'gradle.properties') << """
            ${LegacyCacheCleanupEnablement.CACHE_CLEANUP_PROPERTY}=false
        """.stripIndent()
    }

    void disableCacheCleanupViaDsl() {
        setCleanupInterval("DISABLED")
    }

    void explicitlyEnableCacheCleanupViaDsl() {
        setCleanupInterval("DEFAULT")
    }

    void disableCacheCleanup(CleanupMethod method) {
        switch (method) {
            case CleanupMethod.PROPERTY:
                disableCacheCleanupViaProperty()
                break
            case CleanupMethod.DSL:
                disableCacheCleanupViaDsl()
                break
            case CleanupMethod.BOTH:
                disableCacheCleanupViaProperty()
                disableCacheCleanupViaDsl()
                break
            default:
                throw new IllegalArgumentException()
        }
    }

    void alwaysCleanupCaches() {
        setCleanupInterval("ALWAYS")
    }

    private void setCleanupInterval(String cleanup) {
        def initDir = new File(gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    cleanup = Cleanup.${cleanup}
                }
            }
        """.stripIndent()
    }

    void withReleasedWrappersRetentionInDays(int days) {
        withCacheRetentionInDays(days, "releasedWrappers")
    }

    void withCreatedResourcesRetentionInDays(int days) {
        withCacheRetentionInDays(days, "createdResources")
    }

    void withDownloadedResourcesRetentionInDays(int days) {
        withCacheRetentionInDays(days, "downloadedResources")
    }

    void withCacheRetentionInDays(int days, String resources) {
        def initDir = new File(gradleUserHomeDir, "init.d")
        initDir.mkdirs()
        new File(initDir, "cache-settings.gradle") << """
            beforeSettings { settings ->
                settings.caches {
                    ${resources} { removeUnusedEntriesAfterDays = ${days} }
                }
            }
        """
    }

    abstract TestFile getGradleUserHomeDir()

    enum CleanupMethod {
        PROPERTY("legacy property only", true),
        DSL("DSL only", false),
        BOTH("both legacy property and DSL", false)

        final String description
        final boolean deprecationExpected

        CleanupMethod(String description, boolean deprecationExpected) {
            this.description = description
            this.deprecationExpected = deprecationExpected
        }

        @Override
        String toString() {
            return description
        }

        void maybeExpectDeprecationWarning(executer) {
            if (deprecationExpected) {
                executer.expectDocumentedDeprecationWarning(
                    "Disabling Gradle user home cache cleanup with the 'org.gradle.cache.cleanup' property has been deprecated. " +
                        "This is scheduled to be removed in Gradle 9.0. " +
                        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#disabling_user_home_cache_cleanup"
                )
            }
        }
    }

    GradleDistDirs versionedDistDirs(String version, MarkerFileType lastUsed, String customDistName) {
        def distVersion = GradleVersion.version(version)
        return new GradleDistDirs(
            createVersionSpecificCacheDir(distVersion, lastUsed),
            createDistributionChecksumDir(distVersion).parentFile,
            createCustomDistributionChecksumDir(customDistName, distVersion).parentFile
        )
    }

    static class GradleDistDirs {
        private final TestFile cacheDir
        private final TestFile distDir
        private final TestFile customDistDir

        GradleDistDirs(TestFile cacheDir, TestFile distDir, TestFile customDistDir) {
            this.cacheDir = cacheDir
            this.distDir = distDir
            this.customDistDir = customDistDir
        }

        void assertAllDirsExist() {
            cacheDir.assertExists()
            distDir.assertExists()
            customDistDir.assertExists()
        }

        void assertAllDirsDoNotExist() {
            cacheDir.assertDoesNotExist()
            distDir.assertDoesNotExist()
            customDistDir.assertDoesNotExist()
        }
    }

    static enum DistType {
        RELEASED() {
            @Override
            String version(String baseVersion) {
                return baseVersion
            }

            @Override
            String alternateVersion(String baseVersion) {
                throw new UnsupportedOperationException()
            }
        },
        SNAPSHOT() {
            def formatter = new SimpleDateFormat("yyyyMMddHHmmssZ")
            def now = Instant.now()

            @Override
            String version(String baseVersion) {
                return baseVersion + '-' + formatter.format(Date.from(now))
            }

            @Override
            String alternateVersion(String baseVersion) {
                return baseVersion + '-' + formatter.format(Date.from(now.plusSeconds(60)))
            }
        }

        abstract String version(String baseVersion)
        abstract String alternateVersion(String baseVersion)

        @Override
        String toString() {
            return name().toLowerCase()
        }
    }
}
