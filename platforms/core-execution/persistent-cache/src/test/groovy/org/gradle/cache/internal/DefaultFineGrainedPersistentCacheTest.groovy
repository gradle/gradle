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

import org.gradle.cache.CacheCleanupStrategy
import org.gradle.cache.CleanableStore
import org.gradle.cache.CleanupAction
import org.gradle.cache.CleanupFrequency
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.FineGrainedCacheCleanupStrategy
import org.gradle.cache.internal.filelock.DefaultLockOptions
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FineGrainedPersistentCache.LOCKS_DIR_RELATIVE_PATH

class DefaultFineGrainedPersistentCacheTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def cacheDir = tmpDir.file("fine-grained-cache")

    CleanupAction cleanupAction = Mock(CleanupAction)

    def cacheCleanupStrategy = new FineGrainedCacheCleanupStrategy() {
        @Override
        CacheCleanupStrategy getCleanupStrategy() {
            return new CacheCleanupStrategy() {
                @Override
                void clean(CleanableStore store, Instant lastCleanupTime) {
                    cleanupAction.clean(store, null)
                }

                @Override
                CleanupFrequency getCleanupFrequency() {
                    return CleanupFrequency.DAILY
                }
            }
        }
    }

    FileLockManager lockManager = Mock(FileLockManager)
    FileLock lock = Mock(FileLock)

    @Subject
    @AutoCleanup
    def cache = new DefaultFineGrainedPersistentCache(cacheDir, "<display>", lockManager, cacheCleanupStrategy)

    def "has useful toString() implementation"() {
        expect:
        cache.toString() == "<display>"
    }

    def "open creates directory if it does not exist"() {
        given:
        cacheDir.assertDoesNotExist()

        when:
        cache.open()

        then:
        cacheDir.assertIsDir()
    }

    def "open does nothing when directory already exists"() {
        given:
        cacheDir.createDir()

        when:
        cache.open()

        then:
        notThrown(RuntimeException)
    }

    def "reserved files include only .internal directory"() {
        when:
        cache.open()

        then:
        def reserved = cache.getReservedCacheFiles()
        reserved.contains(cacheDir.file(".internal"))
        !reserved.contains(cacheDir.file(".internal/gc.properties"))
        !reserved.contains(cacheDir.file(LOCKS_DIR_RELATIVE_PATH))
    }

    def "useCache(Supplier) acquires per-key exclusive lock and releases it"() {
        given:
        def key = "entryKey"
        def expectedLockFile = cacheDir.file("${LOCKS_DIR_RELATIVE_PATH}/${key}.lock")
        def lockOptions = DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem()

        when:
        cache.open()
        def result = cache.useCache(key, {
            return 42
        } as Supplier<Integer>)
        cache.close()

        then:
        result == 42
        1 * lockManager.lock(expectedLockFile, lockOptions, "<display>", "acquireLock") >> lock
        1 * lock.close()
        0 * _
    }

    def "useCache(Runnable) acquires per-key exclusive lock and releases it"() {
        given:
        def key = "entryKey"
        def expectedLockFile = cacheDir.file("${LOCKS_DIR_RELATIVE_PATH}/${key}.lock")
        def lockOptions = DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem()

        when:
        cache.open()
        cache.useCache(key) {
        } as Runnable
        cache.close()

        then:
        1 * lockManager.lock(expectedLockFile, lockOptions, "<display>", "acquireLock") >> lock
        1 * lock.close()
        0 * _
    }

    def "withFileLock(Supplier) acquires per-key exclusive lock and releases it"() {
        given:
        def key = "entryKey"
        def expectedLockFile = cacheDir.file("${LOCKS_DIR_RELATIVE_PATH}/${key}.lock")
        def lockOptions = DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem()

        when:
        cache.open()
        def result = cache.withFileLock(key, {
            return 42
        } as Supplier<Integer>)
        cache.close()

        then:
        result == 42
        1 * lockManager.lock(expectedLockFile, lockOptions, "<display>", "acquireLock") >> lock
        1 * lock.close()
        0 * _
    }

    def "withFileLock(Runnable) acquires per-key exclusive lock and releases it"() {
        given:
        def key = "entryKey"
        def expectedLockFile = cacheDir.file("${LOCKS_DIR_RELATIVE_PATH}/${key}.lock")
        def lockOptions = DefaultLockOptions.mode(Exclusive).ensureAcquiredLockRepresentsStateOnFileSystem()

        when:
        cache.open()
        cache.withFileLock(key) {
        } as Runnable
        cache.close()

        then:
        1 * lockManager.lock(expectedLockFile, lockOptions, "<display>", "acquireLock") >> lock
        1 * lock.close()
        0 * _
    }

    def "runs cleanup action when it is due"() {
        when:
        cache.open()
        cache.close()

        then:
        0 * cleanupAction.clean(cache, _)
        gcFile.assertIsFile()

        when:
        markCacheForCleanup(gcFile)
        def modificationTimeBefore = gcFile.lastModified()
        cache.open()
        cache.close()

        then:
        gcFile.lastModified() > modificationTimeBefore
        1 * cleanupAction.clean(cache, _)
        0 * _
    }

    def "fails gracefully if cleanup action fails"() {
        when:
        cache.open()
        cache.close()

        then:
        0 * cleanupAction.clean(cache, _)
        gcFile.assertIsFile()

        when:
        markCacheForCleanup(gcFile)
        def modificationTimeBefore = gcFile.lastModified()
        cache.open()
        cache.close()

        then:
        gcFile.lastModified() == modificationTimeBefore
        1 * cleanupAction.clean(cache, _) >> { throw new RuntimeException("Boom") }
        noExceptionThrown()
    }

    def "does not use gc.properties when no cleanup action is defined"() {
        given:
        def noCleanupStrategy = new FineGrainedCacheCleanupStrategy() {
            @Override
            CacheCleanupStrategy getCleanupStrategy() { return CacheCleanupStrategy.NO_CLEANUP }
        }
        def noCleanupCache = new DefaultFineGrainedPersistentCache(cacheDir, "<display>", lockManager, noCleanupStrategy)

        when:
        noCleanupCache.open()
        noCleanupCache.close()

        then:
        0 * _
        gcFile.assertDoesNotExist()
    }

    def "useCache method rejects invalid key that #description"() {
        when:
        cache.useCache(key) {}

        then:
        def e = thrown(IllegalArgumentException)
        e.message == expectedMessage

        when:
        cache.useCache(key, {
            return 42
        } as Supplier)

        then:
        e = thrown(IllegalArgumentException)
        e.message == expectedMessage

        where:
        key        | description                       | expectedMessage
        "bad/key"  | "contains Unix file separator"    | "Cache key path must not contain file separator: 'bad/key'"
        "bad\\key" | "contains Windows file separator" | "Cache key path must not contain file separator: 'bad\\key'"
        ".hidden"  | "starts with dot"                 | "Cache key must not start with '.' character: '.hidden'"
    }

    def "withFileLock method rejects invalid key that #description"() {
        when:
        cache.withFileLock(key) {}

        then:
        def e = thrown(IllegalArgumentException)
        e.message == expectedMessage

        when:
        cache.withFileLock(key, {
            return 42
        } as Supplier)

        then:
        e = thrown(IllegalArgumentException)
        e.message == expectedMessage

        where:
        key        | description                       | expectedMessage
        "bad/key"  | "contains Unix file separator"    | "Cache key path must not contain file separator: 'bad/key'"
        "bad\\key" | "contains Windows file separator" | "Cache key path must not contain file separator: 'bad\\key'"
        ".hidden"  | "starts with dot"                 | "Cache key must not start with '.' character: '.hidden'"
    }

    private static void markCacheForCleanup(TestFile gcFile) {
        gcFile.setLastModified(gcFile.lastModified() - TimeUnit.DAYS.toMillis(2))
    }

    private TestFile getGcFile() {
        cacheDir.file(".internal/gc.properties")
    }
}
