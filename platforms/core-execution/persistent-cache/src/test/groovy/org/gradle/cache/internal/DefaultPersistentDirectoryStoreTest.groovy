/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject

import java.time.Instant
import java.util.concurrent.TimeUnit

import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.FileLockManager.LockMode.Shared
import static org.gradle.cache.internal.filelock.DefaultLockOptions.mode

class DefaultPersistentDirectoryStoreTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def cacheDir = tmpDir.file("dir")
    def cleanupAction = Mock(CleanupAction)
    def cacheCleanup = new CacheCleanupStrategy() {
        @Override
        void clean(CleanableStore store, Instant lastCleanupTime) {
            cleanupAction.clean(store, null)
        }

        @Override
        CleanupFrequency getCleanupFrequency() {
            return CleanupFrequency.DAILY
        }
    }
    def lockManager = Mock(FileLockManager)
    def lock = Mock(FileLock)

    @Subject @AutoCleanup
    def store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(OnDemand), cacheCleanup, lockManager, Mock(ManagedExecutor))

    def "has useful toString() implementation"() {
        expect:
        store.toString() == "<display> ($cacheDir)"
    }

    def "open creates directory if it does not exist"() {
        given:
        cacheDir.assertDoesNotExist()

        when:
        store.open()

        then:
        cacheDir.assertIsDir()
    }

    def "open does nothing when directory already exists"() {
        given:
        cacheDir.createDir()

        when:
        store.open()

        then:
        notThrown(RuntimeException)
    }

    def "open locks cache directory with requested mode"() {
        final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(Shared), null, lockManager, Mock(ExecutorFactory))

        when:
        store.open()

        then:
        1 * lockManager.lock(cacheDir, mode(Shared), "<display> ($cacheDir)") >> lock

        when:
        store.close()

        then:
        _ * lock.state
        1 * lock.close()
        0 * _._
    }

    def "locks requested target"() {
        final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(Shared), null, lockManager, Mock(ExecutorFactory))

        when:
        store.open()

        then:
        1 * lockManager.lock(cacheDir.file("."), mode(Shared), "<display> ($cacheDir)") >> lock

        when:
        store.close()

        then:
        _ * lock.state
        1 * lock.close()
        0 * _._
    }

    def "open does not lock cache directory when None mode requested"() {
        final store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(OnDemand), null, lockManager, Mock(ExecutorFactory))

        when:
        store.open()

        then:
        0 * _._

        when:
        store.close()

        then:
        0 * _._
    }

    def "runs cleanup action when it is due"() {
        when:
        store.open()
        store.close()

        then:
        0 * _  // Does not call initialization or cleanup action.
        gcFile.assertIsFile()

        when:
        markCacheForCleanup(gcFile)
        def modificationTimeBefore = gcFile.lastModified()
        store.open()
        store.close()

        then:
        gcFile.lastModified() > modificationTimeBefore
        1 * cleanupAction.clean(store, _)
        0 * _
    }

    def "fails gracefully if cleanup action fails"() {
        when:
        store.open()
        store.close()

        then:
        0 * _  // Does not call initialization or cleanup action.
        gcFile.assertIsFile()

        when:
        markCacheForCleanup(gcFile)
        store.open()
        store.close()

        then:
        1 * cleanupAction.clean(store, _) >> {
            throw new RuntimeException("Boom")
        }
        noExceptionThrown()
    }

    def "does not use gc.properties when no cleanup action is defined"() {
        given:
        store = new DefaultPersistentDirectoryStore(cacheDir, "<display>", mode(OnDemand), CacheCleanupStrategy.NO_CLEANUP, lockManager, Mock(ExecutorFactory))

        when:
        store.open()
        store.close()

        then:
        0 * _
        gcFile.assertDoesNotExist()
    }

    private static void markCacheForCleanup(TestFile gcFile) {
        gcFile.setLastModified(gcFile.lastModified() - TimeUnit.DAYS.toMillis(2))
    }

    private TestFile getGcFile() {
        cacheDir.file("gc.properties")
    }

}
