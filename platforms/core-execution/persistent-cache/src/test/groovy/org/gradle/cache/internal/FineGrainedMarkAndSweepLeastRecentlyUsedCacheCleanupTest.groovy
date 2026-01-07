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
        def gcDir = new File(cacheDir, "gc")
        new File(gcDir, key + ".soft.deleted").exists()
        new File(gcDir, key + ".soft.gc").exists()

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
        !new File(cacheDir, "gc").exists()
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
        def gcDir = new File(cacheDir, "gc")
        def softDeleted = new File(gcDir, key + ".soft.deleted")
        def softGc = new File(gcDir, key + ".soft.gc")
        gcDir.mkdirs()
        softDeleted.parentFile.mkdirs()
        softDeleted.text = ""
        softGc.text = ""
        long sevenHoursAgo = now - TimeUnit.HOURS.toMillis(7)
        softDeleted.lastModified = sevenHoursAgo
        softGc.lastModified = sevenHoursAgo

        // Create a lock file for the entry that should be removed on hard delete
        def lockFile = cache.getLockFile(key)
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
        def gcProps = new File(cacheDir, "gc.properties")
        gcProps.text = "keep"
        def locksDir = new File(cacheDir, "locks")
        locksDir.mkdirs()
        def someLock = new File(locksDir, "x.lock")
        someLock.text = "locked"

        // Make them look very old so they would be eligible if not reserved
        long fiveDaysAgo = now - TimeUnit.DAYS.toMillis(5)
        gcProps.lastModified = fiveDaysAgo
        locksDir.lastModified = fiveDaysAgo
        someLock.lastModified = fiveDaysAgo

        // Also create a normal recent entry so cleanup iterates
        def recent = createCacheEntryDir(now)

        when:
        cleanupAction.clean(cache, progressMonitor)

        then:
        // Reserved items are not deleted
        gcProps.exists()
        locksDir.exists()
        someLock.exists()
        recent.exists()

        // And journal is not asked to delete last access time for reserved files
        1 * fileAccessTimeJournal.getLastAccessTime(recent)
        0 * fileAccessTimeJournal.getLastAccessTime(_)
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
        FineGrainedPersistentCache open() { return this }

        @Override
        File getBaseDir() { return baseDir }

        @Override
        Collection<File> getReservedCacheFiles() {
            return [new File(baseDir, "gc.properties"), new File(baseDir, "locks")] as Collection<File>
        }

        @Override
        String getDisplayName() { return displayName }

        @Override
        <T> T useCache(String key, java.util.function.Supplier<? extends T> action) { return action.get() }

        @Override
        void useCache(String key, Runnable action) { action.run() }

        @Override
        void close() {}

        @Override
        void cleanup() {}

        @Override
        File getLockFile(String key) {
            File locksDir = new File(baseDir, "locks")
            locksDir.mkdirs()
            return new File(locksDir, key + ".lock")
        }
    }
}
