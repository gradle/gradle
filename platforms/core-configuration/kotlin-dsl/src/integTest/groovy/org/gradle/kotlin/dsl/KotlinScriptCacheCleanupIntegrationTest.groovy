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
        // Capture the baseline before '--stop': stopping the daemon closes the cache and can itself
        // run the (already-due) cleanup, so the window must cover it, not just the 'run' below.
        def beforeSoftCleanup = MILLISECONDS.toSeconds(System.currentTimeMillis())
        run '--stop' // ensure daemon does not cache file access times in memory
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
        reason = "needs a separate process per build so the file-access-time journal is read from disk; embedded keeps it in memory, so externally backdating an entry the build touched has no effect"
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

    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "unused classpath entry snapshot and ABI hash files are deleted"() {
        given:
        // needs to stop the daemon and have its own journal
        executer.requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("needs its own journal")

        and: 'seed the classpath snapshot cache by compiling a script'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'

        and: 'the snapshot + ABI files the real classpath produced, plus a synthetic entry never on any classpath'
        List<TestFile> realEntries = snapshotEntryFiles('.snapshot') + snapshotEntryFiles('.abi')
        assert !realEntries.isEmpty()
        TestFile staleSnapshot = classpathSnapshotsDir.file('00000000000000000000000000000000.snapshot').tap { assert !exists(); text = 'stale' }
        TestFile staleAbi = classpathSnapshotsDir.file('00000000000000000000000000000000.abi').tap { assert !exists(); text = 'stale' }
        // mark the synthetic entry as last accessed older than retention so it is a candidate for cleanup
        ageForSnapshotCleanup([staleSnapshot, staleAbi])
        forceCleanupDue(classpathSnapshotCacheGcFile)

        when: 'a build runs with cleanup due'
        run '--stop' // ensure the daemon does not cache file access times in memory
        run 'run'

        then: 'the unused synthetic files are deleted, while the entries the build just used remain'
        staleSnapshot.assertDoesNotExist()
        staleAbi.assertDoesNotExist()
        realEntries.every { it.assertExists() }
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a separate process per build so the file-access-time journal is read from disk; embedded keeps it in memory, so externally backdating an entry the build touched has no effect"
    )
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "classpath entry snapshot ages out while avoidance keeps its ABI hash, and is regenerated on recompilation"() {
        given:
        // needs a real daemon process per build and its own journal
        executer.requireDaemon().requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("needs its own journal")

        and: 'compiling a script snapshots its classpath: a .snapshot (for IC) and a .abi (for avoidance) per entry'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'

        and:
        List<TestFile> snapshots = snapshotEntryFiles('.snapshot')
        List<TestFile> abiHashes = snapshotEntryFiles('.abi')
        assert !snapshots.isEmpty()
        assert !abiHashes.isEmpty()

        when: '(1) time passes and the script is not recompiled, so cleanup runs while only avoidance has touched the cache'
        run '--stop' // ensure the daemon does not cache file access times in memory
        ageForSnapshotCleanup(snapshots + abiHashes)
        forceCleanupDue(classpathSnapshotCacheGcFile)
        run 'run' // workspace hit: avoidance reads each .abi (refreshing it), but the script is not recompiled, so no .snapshot is touched

        then: 'the snapshots are reclaimed, but the ABI hashes survive because avoidance refreshed their access time'
        snapshots.every { it.assertDoesNotExist() }
        abiHashes.every { it.assertExists() }

        when: '(2) the script is modified, forcing recompilation, which regenerates the reclaimed snapshots on demand'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok again")
        run 'run' // no cleanup due; recompiling calls snapshotFileFor, which regenerates the missing snapshots

        then:
        snapshots.every { it.assertExists() }
        abiHashes.every { it.assertExists() }
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a fresh daemon so BTA re-reads the corrupted snapshot from disk rather than reusing the in-memory IC state from the seeding build"
    )
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "an unreadable classpath entry snapshot degrades to recompilation rather than failing the build"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("corrupts the snapshot cache")

        and: 'seed the snapshot cache by compiling a script'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'

        and: 'corrupt every cached classpath snapshot — they still exist, so snapshotFileFor hands the garbage straight to BTA instead of regenerating it'
        List<TestFile> snapshots = snapshotEntryFiles('.snapshot')
        assert !snapshots.isEmpty()
        snapshots.each { it.text = 'not a valid snapshot' }

        when: 'a fresh daemon recompiles, re-reading the corrupt snapshots from disk'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok again")

        then: 'the build still succeeds — an unreadable classpath snapshot degrades to recompilation, never a build failure'
        succeeds 'run'
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a fresh daemon per build so BTA re-reads ic-state from disk rather than reusing in-memory IC state"
    )
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "corrupt incremental-compilation state is discarded on fallback so the next build recovers"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("corrupts the IC cache")

        and: 'seed incremental state: the first compile is cold (outputs only), a recompile then records ic-state'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok 2")
        run 'run'

        and: 'corrupt the recorded ic-state so the next incremental attempt throws'
        List<TestFile> icStates = realIcEntries().collect { it.file('ic-state') }.findAll { it.directory && it.list().length > 0 }
        assert !icStates.isEmpty()
        icStates.each { it.eachFileRecurse(groovy.io.FileType.FILES) { f -> f.text = 'corrupt' } }

        when: 'a fresh daemon recompiles: incremental fails on the corrupt ic-state, falls back to a full compile, and discards the ic-state'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok 3")
        run 'run'

        then: 'the build succeeded and the corrupt ic-state was discarded (the full compile does not repopulate it)'
        icStates.every { !it.exists() || it.list().length == 0 }

        when: 'the next build bootstraps from the surviving outputs and records fresh ic-state'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok 4")
        run 'run'

        then: 'incremental compilation is healthy again rather than pinned to the slow path — ic-state is repopulated'
        icStates.every { it.directory && it.list().length > 0 }
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a fresh daemon per build so BTA re-reads ic-state and outputs from disk rather than reusing in-memory state"
    )
    @UnsupportedWithConfigurationCache(because = "tests script compilation")
    def "outputs left torn by an interrupted compile are discarded and rebuilt rather than copied into the workspace"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons()
        requireOwnGradleUserHomeDir("corrupts the IC cache")

        and: 'seed incremental state: a cold compile then a recompile that records ic-state, so the next build would otherwise reach BTA "no work"'
        buildKotlinFile.text = scriptPrinting("ok")
        run 'run'
        run '--stop'
        buildKotlinFile.text = scriptPrinting("ok 2")
        run 'run'

        and: 'simulate a compile killed mid-emit: a surviving in-progress marker plus torn class files in the stable outputs'
        List<TestFile> entries = realIcEntries()
        assert !entries.isEmpty()
        List<TestFile> markers = entries.collect { it.file('compile-in-progress') }
        List<File> tornClasses = []
        entries.collect { it.file('outputs') }.findAll { it.directory }.each {
            it.eachFileRecurse(groovy.io.FileType.FILES) { f -> if (f.name.endsWith('.class')) { tornClasses << f } }
        }
        assert !tornClasses.isEmpty()
        markers.each { it.createFile() }
        tornClasses.each { it.bytes = [0, 1, 2, 3] as byte[] } // invalid bytecode

        and: 'drop the published workspace so the next build must recompile instead of serving the good cached classes'
        scriptCacheDir.deleteDir()

        when: 'a fresh daemon recompiles the unchanged script: BTA would go "no work", but the marker forces a discard-and-rebuild'
        run '--stop'

        then: 'the torn classes never reach the workspace — the build succeeds and the marker is cleared'
        succeeds 'run'
        markers.every { !it.exists() }
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

    private List<TestFile> snapshotEntryFiles(String suffix) {
        return classpathSnapshotsDir.exists()
            ? (classpathSnapshotsDir.list() as List<String>).findAll { it.endsWith(suffix) }.collect { classpathSnapshotsDir.file(it) }
            : []
    }

    private void ageForSnapshotCleanup(List<TestFile> files) {
        // mark each file as last accessed older than retention so it is a candidate for cleanup
        files.each { writeLastFileAccessTimeToJournal(it, daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES + 1)) }
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

    TestFile getClasspathSnapshotCacheDir() {
        return userHomeCacheDir.file(GradleVersion.current().version).file('kotlin-dsl-classpath-snapshots')
    }

    TestFile getClasspathSnapshotsDir() {
        return classpathSnapshotCacheDir.file('snapshots')
    }

    TestFile getClasspathSnapshotCacheGcFile() {
        return classpathSnapshotCacheDir.file('gc.properties')
    }
}
