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

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.USED_TODAY
import static org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES

class GradleUserHomeCleanupServiceIntegrationTest extends AbstractIntegrationSpec implements GradleUserHomeCleanupFixture {
    private static final int HALF_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS = Math.max(1, CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS / 2 as int)
    private static final int HALF_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS = Math.max(1, CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS / 2 as int)

    private static final MarkerFileType NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS = MarkerFileType.notUsedWithinDays(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS)
    private static final MarkerFileType NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_SNAPSHOT_DISTS = MarkerFileType.notUsedWithinDays(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS)
    private static final MarkerFileType NOT_USED_WITHIN_HALF_MAX_DAYS_FOR_RELEASED_DISTS = MarkerFileType.notUsedWithinDays(HALF_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS)
    private static final MarkerFileType NOT_USED_WITHIN_HALF_MAX_DAYS_FOR_SNAPSHOT_DISTS = MarkerFileType.notUsedWithinDays(HALF_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS)

    def "cleans up unused version-specific cache directories and corresponding #type distributions"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(type.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(type.version("2.3.4"), daysToTriggerCleanup, "my-dist-2")
        if (type == DistType.SNAPSHOT) {
            // we need this so there is more than one snapshot distribution
            versionedDistDirs(type.alternateVersion("2.3.4"), USED_TODAY, "my-dist-3")
        }

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), daysToTriggerCleanup)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsDoNotExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        getGcFile(currentCacheDir).assertExists()

        where:
        type              | daysToTriggerCleanup
        DistType.RELEASED | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS
        DistType.SNAPSHOT | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_SNAPSHOT_DISTS
    }

    def "cleans up unused version-specific cache directories and corresponding #type distributions when retention is configured"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions
        withCacheRetentionInDays(retentionInDays, "${type.name().toLowerCase()}Wrappers")

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(type.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(type.version("2.3.4"), daysToTriggerCleanup, "my-dist-2")
        if (type == DistType.SNAPSHOT) {
            // we need this so there is more than one snapshot distribution
            versionedDistDirs(type.alternateVersion("2.3.4"), USED_TODAY, "my-dist-3")
        }

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), daysToTriggerCleanup)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsDoNotExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        getGcFile(currentCacheDir).assertExists()

        where:
        type              | daysToTriggerCleanup                             | retentionInDays
        DistType.RELEASED | NOT_USED_WITHIN_HALF_MAX_DAYS_FOR_RELEASED_DISTS | HALF_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS
        DistType.SNAPSHOT | NOT_USED_WITHIN_HALF_MAX_DAYS_FOR_SNAPSHOT_DISTS | HALF_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS
    }

    def "does not clean up unused version-specific cache directories and corresponding #type distributions when cleanup is disabled"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions
        disableCacheCleanupViaDsl()

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(type.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(type.version("2.3.4"), daysToTriggerCleanup, "my-dist-2")
        if (type == DistType.SNAPSHOT) {
            // we need this so there is more than one snapshot distribution
            versionedDistDirs(type.alternateVersion("2.3.4"), USED_TODAY, "my-dist-3")
        }

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), daysToTriggerCleanup)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        where:
        type              | daysToTriggerCleanup
        DistType.RELEASED | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS
        DistType.SNAPSHOT | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_SNAPSHOT_DISTS
    }

    def "only cleans up unused version-specific cache directories and corresponding #type distributions once when default cleanup is used"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions

        and:
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), daysToTriggerCleanup)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        and:
        succeeds("help")
        def gcFile = getGcFile(currentCacheDir)
        gcFile.assertExists()
        def beforeTimestamp = gcFile.lastModified()

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(type.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(type.version("2.3.4"), daysToTriggerCleanup, "my-dist-2")
        if (type == DistType.SNAPSHOT) {
            // we need this so there is more than one snapshot distribution
            versionedDistDirs(type.alternateVersion("2.3.4"), USED_TODAY, "my-dist-3")
        }

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        and:
        gcFile.lastModified() == beforeTimestamp

        where:
        type              | daysToTriggerCleanup
        DistType.RELEASED | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS
        DistType.SNAPSHOT | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_SNAPSHOT_DISTS
    }

    def "always cleans up unused version-specific cache directories and corresponding #type distributions when configured"() {
        given:
        executer.requireIsolatedDaemons() // because we want to reuse Gradle user home services
        executer.beforeExecute {
            if (!GradleContextualExecuter.embedded) {
                executer.withArgument("-D$REUSE_USER_HOME_SERVICES=true")
            }
        }
        requireOwnGradleUserHomeDir() // because we delete caches and distributions
        alwaysCleanupCaches()

        and:
        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), daysToTriggerCleanup)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile
        def gcFile = getGcFile(currentCacheDir)

        and:
        succeeds("help")
        gcFile.assertExists()
        def beforeTimestamp = gcFile.lastModified()

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(type.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(type.version("2.3.4"), daysToTriggerCleanup, "my-dist-2")
        if (type == DistType.SNAPSHOT) {
            // we need this so there is more than one snapshot distribution
            versionedDistDirs(type.alternateVersion("2.3.4"), USED_TODAY, "my-dist-3")
        }

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsDoNotExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        and:
        gcFile.lastModified() > beforeTimestamp

        where:
        type              | daysToTriggerCleanup
        DistType.RELEASED | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS
        DistType.SNAPSHOT | NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_SNAPSHOT_DISTS
    }

    def "does not clean up unused version-specific cache directories and corresponding distributions when clean is disabled using #cleanupMethod"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions
        disableCacheCleanup(cleanupMethod)

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(DistType.RELEASED.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(DistType.RELEASED.version("2.3.4"), NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS, "my-dist-2")

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        and:
        cleanupMethod.maybeExpectDeprecationWarning(executer)

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        where:
        cleanupMethod << CleanupMethod.values()
    }

    def "cleans up unused version-specific cache directories and corresponding #type distributions when DSL is configured even if legacy property is present"() {
        given:
        requireOwnGradleUserHomeDir() // because we delete caches and distributions
        disableCacheCleanupViaProperty()
        explicitlyEnableCacheCleanupViaDsl()

        and:
        def oldButRecentlyUsedGradleDist = versionedDistDirs(DistType.RELEASED.version("1.4.5"), USED_TODAY, "my-dist-1")
        def oldNotRecentlyUsedGradleDist = versionedDistDirs(DistType.RELEASED.version("2.3.4"), NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS, "my-dist-2")

        def currentCacheDir = createVersionSpecificCacheDir(GradleVersion.current(), NOT_USED_WITHIN_DEFAULT_MAX_DAYS_FOR_RELEASED_DISTS)
        def currentDist = createDistributionChecksumDir(GradleVersion.current()).parentFile

        when:
        succeeds("help")

        then:
        oldButRecentlyUsedGradleDist.assertAllDirsExist()
        oldNotRecentlyUsedGradleDist.assertAllDirsDoNotExist()

        currentCacheDir.assertExists()
        currentDist.assertExists()

        getGcFile(currentCacheDir).assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
