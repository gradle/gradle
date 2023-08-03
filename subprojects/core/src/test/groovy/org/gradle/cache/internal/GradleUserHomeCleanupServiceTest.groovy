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

import org.gradle.api.internal.cache.CacheResourceConfigurationInternal
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.provider.Property
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.time.TimestampSuppliers
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.NOT_USED_WITHIN_30_DAYS
import static org.gradle.cache.internal.VersionSpecificCacheCleanupFixture.MarkerFileType.notUsedWithinDays

class GradleUserHomeCleanupServiceTest extends Specification implements GradleUserHomeCleanupFixture {
    private static final int HALF_DEFAULT_MAX_AGE_IN_DAYS = Math.max(1, CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS/2 as int)
    private static final int TWICE_DEFAULT_MAX_AGE_IN_DAYS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS * 2

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def userHomeDir = temporaryFolder.createDir("user-home")
    def currentCacheDir = createVersionSpecificCacheDir(currentVersion, NOT_USED_WITHIN_30_DAYS)

    def userHomeDirProvider = Stub(GradleUserHomeDirProvider) {
        getGradleUserHomeDirectory() >> userHomeDir
    }
    def cacheBuilderFactory = Mock(GlobalScopedCacheBuilderFactory) {
        getRootDir() >> userHomeDir.createDir("caches")
    }
    def usedGradleVersions = Stub(UsedGradleVersions) {
        getUsedGradleVersions() >> ([] as SortedSet)
    }
    def progressLoggerFactory = Stub(ProgressLoggerFactory)

    def releasedWrappers = Stub(CacheResourceConfigurationInternal) {
        getRemoveUnusedEntriesOlderThanAsSupplier() >> TimestampSuppliers.daysAgo(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_RELEASED_DISTS)
    }
    def snapshotWrappers = Stub(CacheResourceConfigurationInternal) {
        getRemoveUnusedEntriesOlderThanAsSupplier() >> TimestampSuppliers.daysAgo(CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_SNAPSHOT_DISTS)
    }
    def cacheConfigurations = Stub(CacheConfigurationsInternal) {
        getReleasedWrappers() >> releasedWrappers
        getSnapshotWrappers() >> snapshotWrappers
        getCleanupFrequency() >> TestUtil.providerFactory().provider { CleanupFrequency.DAILY }
    }

    def property(Object value) {
        return Stub(Property) {
            get() >> value
        }
    }

    @Subject def cleanupService = new GradleUserHomeCleanupService(
            TestFiles.deleter(),
            userHomeDirProvider,
            cacheBuilderFactory,
            usedGradleVersions,
            progressLoggerFactory,
            cacheConfigurations
    )

    def "cleans up unused version-specific cache directories and deletes distributions for unused versions with the default retention"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.cleanup()

        then:
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "cleans up unused version-specific cache directories and deletes distributions for unused versions when retention is configured"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, notUsedWithinDays(TWICE_DEFAULT_MAX_AGE_IN_DAYS))
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.cleanup()

        then:
        releasedWrappers.getRemoveUnusedEntriesOlderThanAsSupplier() >> TimestampSuppliers.daysAgo(TWICE_DEFAULT_MAX_AGE_IN_DAYS - 1)

        and:
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "does not clean up version-specific cache directories and distributions for unused versions newer than the configured retention"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, notUsedWithinDays(HALF_DEFAULT_MAX_AGE_IN_DAYS))
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.cleanup()

        then:
        releasedWrappers.getRemoveUnusedEntriesOlderThanAsSupplier() >> TimestampSuppliers.daysAgo(TWICE_DEFAULT_MAX_AGE_IN_DAYS)
        usedGradleVersions.getUsedGradleVersions() >> ([ GradleVersion.version('2.3.4') ] as SortedSet)

        and:
        oldCacheDir.assertExists()
        oldDist.assertExists()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "skips cleanup of version-specific caches and distributions if gc.properties has recently been changed"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile

        when:
        getGcFile(currentCacheDir).touch()
        cleanupService.cleanup()

        then:
        oldCacheDir.assertExists()
        oldDist.assertExists()
    }

    def "skips cleanup of version-specific caches and distributions if cleanup has been disabled"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.cleanup()

        then:
        cacheConfigurations.cleanupFrequency >> property(CleanupFrequency.NEVER)

        and:
        oldCacheDir.assertExists()
        oldDist.assertExists()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "cleans up unused version-specific cache directories and deletes distributions for unused versions on stop when clean up has not already occurred"() {
        given:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        when:
        cleanupService.stop()

        then:
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "skips clean up on stop when clean up has already occurred"() {
        when:
        cleanupService.cleanup()

        then:
        cacheConfigurations.getCleanupFrequency() >> property(CleanupFrequency.ALWAYS)

        when:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        and:
        cleanupService.stop()

        then:
        cacheConfigurations.getCleanupFrequency() >> property(CleanupFrequency.ALWAYS)

        and:
        oldCacheDir.assertExists()
        oldDist.assertExists()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    def "can clean up multiple times when configured to"() {
        when:
        cleanupService.cleanup()

        then:
        cacheConfigurations.getCleanupFrequency() >> property(CleanupFrequency.ALWAYS)

        when:
        def oldVersion = GradleVersion.version("2.3.4")
        def oldCacheDir = createVersionSpecificCacheDir(oldVersion, NOT_USED_WITHIN_30_DAYS)
        def oldDist = createDistributionChecksumDir(oldVersion).parentFile
        def currentDist = createDistributionChecksumDir(currentVersion).parentFile

        and:
        cleanupService.cleanup()

        then:
        cacheConfigurations.getCleanupFrequency() >> property(CleanupFrequency.ALWAYS)

        and:
        oldCacheDir.assertDoesNotExist()
        oldDist.assertDoesNotExist()
        currentCacheDir.assertExists()
        currentDist.assertExists()
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return userHomeDir
    }
}
