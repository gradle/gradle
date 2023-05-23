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


import org.gradle.cache.internal.GradleUserHomeCleanupFixture
import org.gradle.caching.internal.DefaultBuildCacheKey
import org.gradle.caching.internal.NextGenBuildCacheService
import org.gradle.caching.internal.StatefulNextGenBuildCacheService
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files
import java.util.concurrent.TimeUnit

// TODO: Maybe make abstract BuildCacheCleanupIntegrationTest and use it also with DirectoryBuildCacheCleanupIntegrationTest?
class H2BuildCacheCleanupIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, GradleUserHomeCleanupFixture {
    private final static int MAX_CACHE_AGE_IN_DAYS = 7

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        settingsFile << configureCacheEviction()
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

    static def configureCacheEviction() {
        """
            buildCache {
                local {
                    removeUnusedEntriesAfterDays = ${MAX_CACHE_AGE_IN_DAYS}
                }
            }
        """
    }

    def "cleans up entries"() {
        def lastCleanupCheck = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)
        gcFile().setLastModified(lastCleanupCheck)
        def hashStringLength = Hashing.defaultFunction().hexDigits

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        writeEntryToDb("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        writeEntryToDb("1" * hashStringLength, oldTrashFile, daysAgo(MAX_CACHE_AGE_IN_DAYS + 1))
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        isEntryInDb("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheForCleanup()
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        !isEntryInDb("1" * hashStringLength)
        assertCacheWasCleanedUpSince(lastCleanupCheck)
    }

    def "cleans up entries even if gradle user home cache cleanup is disabled via #cleanupMethod"() {
        disableCacheCleanup(cleanupMethod)
        def lastCleanupCheck = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)
        gcFile().setLastModified(lastCleanupCheck)

        def hashStringLength = Hashing.defaultFunction().hexDigits

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        writeEntryToDb("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        writeEntryToDb("1" * hashStringLength, oldTrashFile, daysAgo(MAX_CACHE_AGE_IN_DAYS + 1))
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        isEntryInDb("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheForCleanup()
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        !isEntryInDb("1" * hashStringLength)
        assertCacheWasCleanedUpSince(lastCleanupCheck)

        where:
        cleanupMethod << CleanupMethod.values()
    }

    def "cleans up entries even if created resource cache cleanup is configured later than the default"() {
        executer.requireIsolatedDaemons() // needs to stop daemon
        requireOwnGradleUserHomeDir() // needs its own journal
        withCreatedResourcesRetentionInDays(MAX_CACHE_AGE_IN_DAYS * 2)
        run() // Make sure cache directory is initialized
        run '--stop' // ensure daemon does not cache file access times in memory
        def lastCleanupCheck = gcFile().makeOlder().lastModified()

        def hashStringLength = Hashing.defaultFunction().hexDigits

        when:
        def newTrashFile = temporaryFolder.file("0" * hashStringLength).createFile()
        def oldTrashFile = temporaryFolder.file("1" * hashStringLength).createFile()
        writeEntryToDb("0" * hashStringLength, newTrashFile, System.currentTimeMillis())
        writeEntryToDb("1" * hashStringLength, oldTrashFile, daysAgo((MAX_CACHE_AGE_IN_DAYS * 2) - 1))
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        isEntryInDb("1" * hashStringLength)
        assertCacheWasNotCleanedUpSince(lastCleanupCheck)

        when:
        lastCleanupCheck = markCacheForCleanup()
        run()

        then:
        isEntryInDb("0" * hashStringLength)
        !isEntryInDb("1" * hashStringLength)
        assertCacheWasCleanedUpSince(lastCleanupCheck)
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
        withBuildCacheNg().fails("help")
        // TODO: don't say Directory build cache in error message
        failure.assertHasCause("Directory build cache needs to retain entries for at least a day.")

        where:
        days << [-1, 0]
    }

    long markCacheForCleanup() {
        gcFile().touch()
        gcFile().lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_CACHE_AGE_IN_DAYS) * 2
        return gcFile().lastModified()
    }

    private void writeEntryToDb(String key, File file, long timestamp) {
        try (StatefulNextGenBuildCacheService cacheService = new H2BuildCacheService(cacheDir.toPath(), 10, Integer.MAX_VALUE, { timestamp })) {
            cacheService.open()
            cacheService.store(new DefaultBuildCacheKey(HashCode.fromString(key)), new NextGenBuildCacheService.NextGenWriter() {
                @Override
                InputStream openStream() throws IOException {
                    return new FileInputStream(file)
                }

                @Override
                void writeTo(OutputStream output) throws IOException {
                    Files.copy(file.toPath(), output)
                }

                @Override
                long getSize() {
                    return file.size()
                }
            })
        }
    }


    private boolean isEntryInDb(String key) {
        try (StatefulNextGenBuildCacheService cacheService = new H2BuildCacheService(cacheDir.toPath(), 10, Integer.MAX_VALUE, Time.clock())) {
            cacheService.open()
            def buildCacheKey = new DefaultBuildCacheKey(HashCode.fromString(key))
            cacheService.contains(buildCacheKey)
        }
    }

    private ExecutionResult run() {
        withBuildCacheNg().succeeds("cacheable")
    }

    private void assertCacheWasCleanedUpSince(long lastCleanupCheck) {
        operations.only("Clean up Build cache NG ($cacheDir)")
        gcFile().lastModified() > lastCleanupCheck
    }

    private void assertCacheWasNotCleanedUpSince(long lastCleanupCheck) {
        operations.none("Clean up Build cache NG ($cacheDir)")
        gcFile().lastModified() == lastCleanupCheck
    }

    private long daysAgo(long days) {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
    }

    @Override
    TestFile getGradleUserHomeDir() {
        return executer.gradleUserHomeDir
    }
}
