/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local.internal

import org.gradle.cache.internal.DefaultPersistentDirectoryStore
import org.gradle.cache.internal.GradleUserHomeCleanupFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile

import java.util.concurrent.TimeUnit

abstract class AbstractBuildCacheCleanupIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, FileAccessTimeJournalFixture, GradleUserHomeCleanupFixture {
    private final static int DEFAULT_RETENTION_PERIOD_DAYS = 7

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)
    def hashStringLength = Hashing.defaultFunction().hexDigits

    abstract String getBuildCacheName();
    abstract void createBuildCacheEntry(String key, File value, long timestamp);
    abstract boolean existsBuildCacheEntry(String key);
    abstract AbstractIntegrationSpec withEnabledBuildCache();

    def setup() {
        def bytes = new byte[1024 * 1024]
        new Random().nextBytes(bytes)
        file("output.txt").bytes = bytes

        buildFile << """
            @CacheableTask
            abstract class CustomTask extends DefaultTask {
                @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                @Input String run = project.findProperty("run") ?: ""
                @javax.inject.Inject abstract FileSystemOperations getFs()
                @TaskAction
                void generate() {
                    logger.warn("Run " + run)
                    fs.copy {
                        from("output.txt")
                        into temporaryDir
                    }
                }
            }

            task cacheable(type: CustomTask) {
                description = "Generates a 1MB file"
            }
        """
    }

    def withDeprecatedBuildCacheRetentionInDays(long period) {
        settingsFile << """
            buildCache {
                local {
                    removeUnusedEntriesAfterDays = ${period}
                }
            }
        """
    }

    def expectRetentionMethodDeprecationWarning() {
        executer.expectDocumentedDeprecationWarning(
            "The DirectoryBuildCache.removeEntriesAfterDays property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#directory_build_cache_retention_deprecated"
        )
    }

    @ToBeFixedForConfigurationCache(because = "Cache is cleaned twice on load after store")
    def "cleans up entries when #cleanupTrigger"() {
        long lastCleanupCheck = initializeHome()

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        createBuildCacheEntry("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        createBuildCacheEntry("1" * hashStringLength, oldTrashFile, daysAgo(DEFAULT_RETENTION_PERIOD_DAYS + 1))
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        existsBuildCacheEntry("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        if (alwaysCleanup) {
            alwaysCleanupCaches()
        } else {
            markCacheLastCleaned(twoDaysAgo())
        }
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        !existsBuildCacheEntry("1" * hashStringLength)
        assertCacheWasCleanedUpSince(lastCleanupCheck)

        where:
        cleanupTrigger              | alwaysCleanup
        "check interval has passed" | false
        "explicitly enabled"        | true
    }

    def "cleans up entries even if gradle user home cache cleanup is disabled via #cleanupMethod"() {
        def lastCleanupCheck = initializeHome()

        disableCacheCleanup(cleanupMethod)

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        createBuildCacheEntry("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        createBuildCacheEntry("1" * hashStringLength, oldTrashFile, daysAgo(DEFAULT_RETENTION_PERIOD_DAYS + 1))
        cleanupMethod.maybeExpectDeprecationWarning(executer)
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        existsBuildCacheEntry("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheLastCleaned(twoDaysAgo())
        executer.noDeprecationChecks()
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        existsBuildCacheEntry("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        where:
        cleanupMethod << CleanupMethod.values()
    }

    def "cleans up entries after #scenario"() {
        def lastCleanupCheck = initializeHome()

        if (buildCacheCleanup != null) {
            withBuildCacheRetentionInDays(buildCacheCleanup)
        }
        if (deprecatedCacheCleanup != null) {
            withDeprecatedBuildCacheRetentionInDays(deprecatedCacheCleanup)
        }

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        createBuildCacheEntry("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        createBuildCacheEntry("1" * hashStringLength, oldTrashFile, daysAgo(effectiveCleanup - 1))
        createBuildCacheEntry("2" * hashStringLength, oldTrashFile, daysAgo(effectiveCleanup + 1))
        if (deprecatedCacheCleanup != null) {
            expectRetentionMethodDeprecationWarning()
        }
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        existsBuildCacheEntry("1" * hashStringLength)
        existsBuildCacheEntry("2" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheLastCleaned(twoDaysAgo())
        executer.noDeprecationChecks()
        run()

        then:
        existsBuildCacheEntry("0" * hashStringLength)
        existsBuildCacheEntry("1" * hashStringLength)
        !existsBuildCacheEntry("2" * hashStringLength)
        assertCacheWasCleanedUpSince(lastCleanupCheck)

        where:
        buildCacheCleanup | deprecatedCacheCleanup | effectiveCleanup | scenario
        null              | null                   | 7                | "default period when not explicitly configured"
        2                 | null                   | 2                | "configured period for build cache cleanup"
        null              | 2                      | 2                | "configured period for deprecated build cache cleanup"
        1                 | 10                     | 10               | "configured period for deprecated build cache cleanup when both are configured"
    }

    def "produces reasonable message when cache retention is too short (#days days)"() {
        settingsFile << """
            buildCache {
                local {
                    removeUnusedEntriesAfterDays = ${days}
                }
            }
        """
        expect:
        runAndFail()
        // TODO: Change name to Build cache, since Directory build cache is not the only cache now
        failure.assertHasCause("Directory build cache needs to retain entries for at least a day.")

        where:
        days << [-1, 0]
    }

    def "cleanup is triggered after max number of hours expires"() {
        def originalCheckTime = initializeHome()

        // One hour isn't enough to trigger
        when:
        // Set the time back 1 hour
        def lastCleanupCheck = markCacheLastCleaned(originalCheckTime - TimeUnit.HOURS.toMillis(1))
        run()

        then:
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        // checkInterval-1 hours is not enough to trigger
        when:
        def twentyThreeHoursAgo = originalCheckTime - TimeUnit.HOURS.toMillis(DefaultPersistentDirectoryStore.CLEANUP_INTERVAL_IN_HOURS - 1)
        lastCleanupCheck = markCacheLastCleaned(twentyThreeHoursAgo)
        run()
        then:
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        // checkInterval hours is enough to trigger
        when:
        def twentyFourHoursAgo = originalCheckTime - TimeUnit.HOURS.toMillis(DefaultPersistentDirectoryStore.CLEANUP_INTERVAL_IN_HOURS)
        lastCleanupCheck = markCacheLastCleaned(twentyFourHoursAgo)
        run()
        then:
        assertCacheWasCleanedUpSince(lastCleanupCheck)

        // More than checkInterval hours is enough to trigger
        when:
        def twentyFiveHoursAgo = originalCheckTime - TimeUnit.HOURS.toMillis(DefaultPersistentDirectoryStore.CLEANUP_INTERVAL_IN_HOURS + 1)
        lastCleanupCheck = markCacheLastCleaned(twentyFiveHoursAgo)
        run()
        then:
        assertCacheWasCleanedUpSince(lastCleanupCheck)
    }

    def "buildSrc does not try to clean build cache"() {
        // Copy cache configuration
        file("buildSrc/settings.gradle").text = settingsFile.text
        def lastCleanupCheck = initializeHome()

        when:
        run()
        then:
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheLastCleaned(twoDaysAgo())
        run()
        then:
        assertCacheWasCleanedUpSince(lastCleanupCheck)
    }

    def "GradleBuild tasks do not try to clean build cache"() {
        file("included/build.gradle") << """
            apply plugin: 'java'
            group = "com.example"
            version = "2.0"
        """
        // Copy cache configuration
        file("included/settings.gradle").text = settingsFile.text
        buildFile << """
            task gradleBuild(type: GradleBuild) {
                dir = file("included/")
                tasks = [ "build" ]
            }

            cacheable {
                dependsOn gradleBuild
            }
        """

        def lastCleanupCheck = initializeHome()

        expect:
        run()
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)
    }

    private long initializeHome() {
        executer.requireIsolatedDaemons() // needs to stop daemon
        requireOwnGradleUserHomeDir() // needs its own journal
        run() // Make sure cache directory is initialized
        run '--stop' // ensure daemon does not cache file access times in memory
        return gcFile().makeOlder().lastModified()
    }

    long markCacheLastCleaned(long timeMillis) {
        gcFile().lastModified = timeMillis
        return gcFile().lastModified()
    }

    ExecutionResult run() {
        withEnabledBuildCache().succeeds("cacheable")
    }

    ExecutionResult runAndFail() {
        withEnabledBuildCache().fails("cacheable")
    }

    void assertCacheWasCleanedUpSince(long lastCleanupCheck) {
        def buildOp = operations.only("Clean up ${getBuildCacheName()} ($cacheDir)")
        buildOp.details.cacheLocation == cacheDir
        assert gcFile().lastModified() > lastCleanupCheck
    }

    void assertCacheWasNotCleanedUpSince(long lastCleanupCheck) {
        operations.none("Clean up ${getBuildCacheName()} ($cacheDir)")
        assert gcFile().lastModified() == lastCleanupCheck
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
