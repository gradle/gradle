/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.cache.CleanupProgressMonitor
import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.internal.file.nio.ModificationTimeFileAccessTimeJournal
import org.gradle.internal.time.TimestampSuppliers
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import static org.gradle.cache.FineGrainedPersistentCache.LOCKS_DIR_RELATIVE_PATH

class FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanupTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    private int entryId = 0
    def cacheDir = temporaryFolder.file("cache-dir").createDir()
    def fileAccessTimeJournal = Spy(ModificationTimeFileAccessTimeJournal)
    def progressMonitor = Stub(CleanupProgressMonitor)
    def cache = new TestFineGrainedCache(cacheDir, "test-cache")

    @Subject
    def cleanupAction = new FineGrainedMarkAndSweepLeastRecentlyUsedCacheCleanup(
        fileAccessTimeJournal, TimestampSuppliers.daysAgo(1)
    )

    def "soft deletes old entries when files are old"() {
        given:
        long now = System.currentTimeMillis()
        long fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        def cacheEntries = [
            createCacheEntryDir(now),
            createCacheEntryDir(now),
            createCacheEntryDir(now),
            createCacheEntryDir(fiveDaysAgo),
        ]

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        cacheEntries[0].assertIsDir()
        cacheEntries[1].assertIsDir()
        cacheEntries[2].assertIsDir()
        cacheEntries[3].assertIsDir() // old entry should be only soft-deleted first

        // Soft-delete markers created for the old entry
        def key = cacheEntries[3].name
        def gcDir = new File(cacheDir, ".internal/gc")
        new File(gcDir, key + "/soft.deleted").exists()
        new File(gcDir, key + "/gc.properties").exists()

        // No journal deletion when only soft-deleted
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    def "does not delete or mark when files are new"() {
        given:
        long now = System.currentTimeMillis()
        def cacheEntries = [
            createCacheEntryDir(now),
            createCacheEntryDir(now - TimeUnit.MINUTES.toMillis(15)),
            createCacheEntryDir(now - TimeUnit.HOURS.toMillis(5)),
        ]

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        cacheEntries[0].assertIsDir()
        cacheEntries[1].assertIsDir()
        cacheEntries[2].assertIsDir()
        !new File(cacheDir, ".internal/gc").exists()
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    def "hard deletes entries after soft deletion window"() {
        given:
        long now = System.currentTimeMillis()
        // create three recent entries and one that was soft-deleted long ago
        def recentA = createCacheEntryDir(now)
        def recentB = createCacheEntryDir(now)
        def recentC = createCacheEntryDir(now)

        def toDelete = createCacheEntryDir(now - TimeUnit.DAYS.toMillis(5))
        def key = toDelete.name

        // Simulate that entry was already soft-deleted more than 6 hours ago
        def gcDir = new File(cacheDir, ".internal/gc")
        def keyGcDir = new File(gcDir, key)
        def softDeleted = new File(keyGcDir, "soft.deleted")
        def softGc = new File(keyGcDir, "gc.properties")
        keyGcDir.mkdirs()
        softDeleted.text = ""
        softGc.text = ""
        long sevenHoursAgo = now - TimeUnit.HOURS.toMillis(7)
        softDeleted.lastModified = sevenHoursAgo
        softGc.lastModified = sevenHoursAgo

        // Create a lock file for the entry that should be removed on hard delete
        def locksDir = new File(cacheDir, LOCKS_DIR_RELATIVE_PATH)
        def lockFile = new File(locksDir, key + ".lock")
        lockFile.parentFile.mkdirs()
        lockFile.text = "locked"

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        recentA.assertIsDir()
        recentB.assertIsDir()
        recentC.assertIsDir()
        toDelete.assertDoesNotExist() // hard deleted
        !softDeleted.exists()
        !softGc.exists()
        !lockFile.exists()
        1 * fileAccessTimeJournal.deleteLastAccessTime(toDelete)
    }

    def "does not delete or touch gc folder and locks folder"() {
        given:
        long now = System.currentTimeMillis()

        // Create reserved gc.properties and locks directory with a lock file
        def internalDir = new File(cacheDir, ".internal")
        internalDir.mkdirs()
        def gcProps = new File(internalDir, "gc.properties")
        gcProps.text = "keep"
        def locksDir = new File(internalDir, "locks")
        locksDir.mkdirs()
        def gcFolder = new File(internalDir, "gc")
        gcFolder.mkdirs()
        // Create a cache entry
        def recent = createCacheEntryDir(now)

        // Make them look very old so they would be eligible if not reserved
        long fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        gcProps.lastModified = fiveDaysAgo
        locksDir.lastModified = fiveDaysAgo
        gcFolder.lastModified = fiveDaysAgo

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        // Reserved items are not deleted
        gcProps.exists()
        locksDir.exists()
        gcFolder.exists()
        recent.exists()

        // And journal is not invoked for reserved files
        1 * fileAccessTimeJournal.getLastAccessTime(recent)
        0 * fileAccessTimeJournal.getLastAccessTime(_)
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    def "deletes orphan lock files while keeping non-orphan locks"() {
        given:
        long now = System.currentTimeMillis()

        // Create locks dir and an orphan lock file
        def locksDir = new File(cacheDir, LOCKS_DIR_RELATIVE_PATH)
        locksDir.mkdirs()
        def orphanLock = new File(locksDir, "orphan.lock")
        orphanLock.text = "locked"

        // Create a cache entry and a corresponding non-orphan lock
        def entry = createCacheEntryDir(now)
        def nonOrphanLock = new File(locksDir, entry.name + ".lock")
        nonOrphanLock.text = "locked"

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        !orphanLock.exists()
        nonOrphanLock.exists()
        entry.exists()

        // No journal deletions due to lock-only changes
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    def "deletes orphan soft-delete gc directories but keeps gc for existing entries"() {
        given:
        long now = System.currentTimeMillis()

        // Create an entry that should be kept and its gc key dir with markers
        def aliveEntry = createCacheEntryDir(now)
        def aliveKey = aliveEntry.name
        def gcRoot = new File(cacheDir, ".internal/gc")
        def aliveGcDir = new File(gcRoot, aliveKey)
        def aliveMarker = new File(aliveGcDir, "soft.deleted")
        def aliveGcProps = new File(aliveGcDir, "gc.properties")
        aliveMarker.parentFile.mkdirs()
        aliveMarker.text = ""
        aliveGcProps.text = ""

        // Create an orphan gc key dir for a non-existing entry
        def orphanKey = "orphan-key"
        def orphanGcDir = new File(gcRoot, orphanKey)
        def orphanMarker = new File(orphanGcDir, "soft.deleted")
        def orphanGcProps = new File(orphanGcDir, "gc.properties")
        orphanMarker.parentFile.mkdirs()
        orphanMarker.text = ""
        orphanGcProps.text = ""

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        // Orphan gc dir is removed
        !orphanGcDir.exists()
        // GC for existing entry is kept intact
        aliveEntry.exists()
        aliveGcDir.exists()
        aliveMarker.exists()
        aliveGcProps.exists()
        // No journal activity as only gc metadata changed
        0 * fileAccessTimeJournal.deleteLastAccessTime(_)
    }

    def createCacheEntryDir(long timestamp) {
        // use a stable, deterministic counter for directory name
        int id = entryId++
        def entryDir = cacheDir.file(String.format("%032x", id)).createDir()
        def content = new File(entryDir, "content.bin")
        content.text = "This is content with id: $id"
        content.lastModified = timestamp
        entryDir.lastModified = timestamp
        return entryDir
    }

    private static class TestFineGrainedCache implements FineGrainedPersistentCache {
        final File baseDir
        final String displayName

        TestFineGrainedCache(File baseDir, String displayName) {
            this.baseDir = baseDir
            this.displayName = displayName
        }

        @Override
        FineGrainedPersistentCache open() {
            return this
        }

        @Override
        File getBaseDir() {
            return baseDir
        }

        @Override
        Collection<File> getReservedCacheFiles() {
            return [new File(baseDir, ".internal")] as Collection<File>
        }

        @Override
        String getDisplayName() {
            return displayName
        }

        @Override
        <T> T useCache(String key, Supplier<? extends T> action) {
            return action.get()
        }

        @Override
        void useCache(String key, Runnable action) {
            action.run()
        }

        @Override
        <T> T withFileLock(String key, Supplier<? extends T> action) {
            return action.get()
        }

        @Override
        void withFileLock(String key, Runnable action) {
            action.run()
        }

        @Override
        void close() {}

        @Override
        void cleanup() {}
    }
}
