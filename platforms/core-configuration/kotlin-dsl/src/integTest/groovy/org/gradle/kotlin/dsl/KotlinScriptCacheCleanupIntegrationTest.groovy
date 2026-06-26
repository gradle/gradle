/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS
import static org.gradle.api.internal.cache.CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES

class KotlinScriptCacheCleanupIntegrationTest
    extends AbstractIntegrationSpec
    implements FileAccessTimeJournalFixture {

    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "cleanup deletes old script cache entries"() {
        given:
        // needs to stop daemon and have its own journal
        executer.requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("needs its own journal")

        and: 'seed script cache to have a baseline to compare against'
        buildKotlinFile.text = """
            tasks.register("run") {
                doLast { println("ok") }
            }
        """
        run 'run'

        and:
        String[] scriptCacheBaseLine = scriptCacheDir.list()
        TestFile outdatedScriptCache = scriptCacheDir.file('7c8e05b2aa9d61f6b8422a683803c455').tap {
            assert !exists()
            file('classes/Program.class').createFile()
        }
        TestFile gcFile = scriptCacheDir.file('.internal/gc.properties')
        gcFile.createFile().lastModified = daysAgo(2)
        writeJournalInceptionTimestamp(daysAgo(8))
        // mark the outdated script cache as last accessed older than retention so it is a candidate for cleanup
        writeLastFileAccessTimeToJournal(outdatedScriptCache, daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1))

        when: 'trigger first cleanup pass (soft delete)'
        run '--stop' // ensure daemon does not cache file access times in memory
        def beforeSoftCleanup = MILLISECONDS.toSeconds(System.currentTimeMillis())
        // start as new process so journal is not restored from in-memory cache
        run "run"

        then: 'outdated entry is soft-deleted (kept with markers) and baseline entries are still present'
        outdatedScriptCache.exists()
        isSoftDeleted(scriptCacheDir, outdatedScriptCache.name)
        locksAndSoftDeletionFilesExist(scriptCacheDir, outdatedScriptCache.name)
        gcFile.lastModified() >= SECONDS.toMillis(beforeSoftCleanup)

        when: 'simulate passage of time beyond soft deletion window and trigger cleanup again (hard delete)'
        def beforeHardCleanup = MILLISECONDS.toSeconds(System.currentTimeMillis())
        def sevenHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(7)
        setSoftDeletedTime(scriptCacheDir, outdatedScriptCache.name, sevenHoursAgo)
        gcFile.lastModified = daysAgo(2)
        run "run"

        then: 'outdated entry directory is hard deleted and markers removed; baseline remains'
        outdatedScriptCache.assertDoesNotExist()
        locksAndSoftDeletionFilesAreDeleted(scriptCacheDir, outdatedScriptCache.name)
        // Baseline entries are still the same
        scriptCacheDir.list() as Set == scriptCacheBaseLine as Set
        gcFile.lastModified() >= SECONDS.toMillis(beforeHardCleanup)
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a separate process per build so the file-access-time journal is read from disk; " +
            "embedded keeps it in memory, so externally backdating an entry the build touched has no effect"
    )
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "incremental compilation cache entry is kept alive by recompilation and reclaimed once unused"() {
        given:
        // needs a real daemon process per build and its own journal
        executer.requireDaemon().requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("needs its own journal")

        and: 'seed the IC cache by compiling a script'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'

        and: 'the IC entries that compiling the script produced'
        List<TestFile> entries = realIcEntries()
        assert !entries.isEmpty()
        TestFile gcFile = icCacheDir.file('.internal/gc.properties')

        when: '(1) time passes with the script unused, so a cleanup soft-deletes its entries'
        run '--stop' // ensure the daemon does not cache file access times in memory
        ageForCleanup(entries)
        forceCleanupDue(gcFile)
        run 'run' // workspace hit: the script is not recompiled, so its entries are not touched

        then:
        entries.every { it.exists() && isSoftDeleted(icCacheDir, it.name) }

        when: '(2) the script is modified, so recompilation reuses its entries and clears the soft-deletion'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok again") // same identity (content-independent), so the entries are reused
        run 'run' // no cleanup is due this build; recompiling clears the soft-delete marker in withScriptState

        then:
        entries.every { it.exists() && !isSoftDeleted(icCacheDir, it.name) }

        when: '(3) more time passes but the entries were just used, so the next cleanup keeps them'
        run '--stop'
        forceCleanupDue(gcFile) // do NOT re-age: step (2) refreshed their access time
        run 'run'

        then:
        entries.every { it.exists() && !isSoftDeleted(icCacheDir, it.name) }

        when: '(4) more time passes with the script unused again, so a cleanup soft-deletes the entries'
        run '--stop'
        ageForCleanup(entries)
        forceCleanupDue(gcFile)
        run 'run'

        then:
        entries.every { it.exists() && isSoftDeleted(icCacheDir, it.name) }

        when: '(5) the soft-deletion window elapses, so the next cleanup hard-deletes them'
        run '--stop'
        def sevenHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(7)
        entries.each { setSoftDeletedTime(icCacheDir, it.name, sevenHoursAgo) }
        forceCleanupDue(gcFile)
        run 'run'

        then:
        entries.every { it.assertDoesNotExist() && locksAndSoftDeletionFilesAreDeleted(icCacheDir, it.name) }
    }

    private static String scriptPrinting(String message) {
        return """
            tasks.register("run") {
                doLast { println("$message") }
            }
        """
    }

    private List<TestFile> realIcEntries() {
        return (icCacheDir.list() as List<String>)
            .findAll { it != '.internal' }
            .collect { icCacheDir.file(it) }
            .findAll { it.directory }
    }

    private void ageForCleanup(List<TestFile> entries) {
        def old = daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1)
        writeJournalInceptionTimestamp(old)
        entries.each {
            // mark the entry as last accessed older than retention so it is a candidate for cleanup
            writeLastFileAccessTimeToJournal(it, old)

            // gc.properties records when this entry was last soft-deleted. The cleanup will not soft-delete
            // an entry again until that timestamp is older than the retention period.
            def lastSoftDeleteMarker = new TestFile(icCacheDir, ".internal/gc/${it.name}/gc.properties")
            if (lastSoftDeleteMarker.exists()) {
                lastSoftDeleteMarker.lastModified = old
            }
        }
    }

    private void forceCleanupDue(TestFile gcFile) {
        if (!gcFile.exists()) {
            gcFile.createFile()
        }
        // pretend the last cleanup ran days ago so the DAILY frequency check fires again
        gcFile.lastModified = daysAgo(2)
    }

    boolean isSoftDeleted(TestFile cacheDir, String key) {
        return new TestFile(cacheDir, ".internal/gc/${key}/soft.deleted").exists()
            && new TestFile(cacheDir, ".internal/gc/${key}/gc.properties").exists()
    }

    void setSoftDeletedTime(TestFile cacheDir, String key, long millis) {
        def keyGcDir = new TestFile(cacheDir, ".internal/gc/${key}")
        def softDeleted = keyGcDir.file("soft.deleted")
        def softGc = keyGcDir.file("gc.properties")
        softDeleted.lastModified = millis
        softGc.lastModified = millis
    }

    boolean locksAndSoftDeletionFilesExist(TestFile cacheDir, String key) {
        return new TestFile(cacheDir, ".internal/locks/${key}.lock").exists()
            && new TestFile(cacheDir, ".internal/gc/${key}").exists()
    }

    boolean locksAndSoftDeletionFilesAreDeleted(TestFile cacheDir, String key) {
        return !new TestFile(cacheDir, ".internal/locks/${key}.lock").exists()
            && !new TestFile(cacheDir, ".internal/gc/${key}").exists()
    }

    TestFile getScriptCacheDir() {
        return userHomeCacheDir.file(GradleVersion.current().version).file('kotlin-dsl').file('scripts')
    }

    TestFile getIcCacheDir() {
        return userHomeCacheDir.file(GradleVersion.current().version).file('kotlin-dsl-ic')
    }
}
