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

import org.gradle.cache.PersistentIndexedCacheParameters
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.internal.Factory
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.cache.internal.FileLockManager.LockMode.*
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultCacheAccessTest extends ConcurrentSpec {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final FileLockManager lockManager = Mock()
    final CacheInitializationAction initializationAction = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final File cacheDir = tmpDir.file('caches')
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    DefaultCacheAccess access = newAccess()

    private DefaultCacheAccess newAccess() {
        new DefaultCacheAccess("<display-name>", lockFile, cacheDir, lockManager, initializationAction, executorFactory) {
            @Override
            def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                return backingCache
            }
        }
    }

    def "acquires lock on open and releases on close when lock mode is shared"() {
        when:
        access.open(mode(Shared))

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state
        0 * _._

        and:
        access.owner == Thread.currentThread()

        when:
        access.close()

        then:
        _ * lock.state
        1 * lock.close()
        0 * _._

        and:
        !access.owner
    }

    def "acquires lock on open and releases on close when lock mode is exclusive"() {
        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state
        0 * _._

        and:
        access.owner == Thread.currentThread()

        when:
        access.close()

        then:
        _ * lock.state
        1 * lock.close()
        0 * _._

        and:
        !access.owner
    }

    def "initializes cache on open when lock mode is shared by upgrading lock"() {
        def exclusiveLock = Mock(FileLock)
        def sharedLock = Mock(FileLock)

        when:
        access.open(mode(Shared))

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.close()

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> exclusiveLock
        1 * initializationAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(exclusiveLock)
        1 * exclusiveLock.close()

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> sharedLock
        1 * initializationAction.requiresInitialization(sharedLock) >> false
        _ * sharedLock.state
        0 * _._

        and:
        access.owner == Thread.currentThread()
    }

    def "initializes cache on open when lock mode is exclusive"() {
        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        _ * lock.state
        0 * _._

        and:
        access.owner == Thread.currentThread()
    }

    def "cleans up when cache validation fails"() {
        def failure = new RuntimeException()

        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> { throw failure }
        1 * lock.close()
        0 * _._

        and:
        RuntimeException e = thrown()
        e == failure
    }

    def "cleans up when initialization fails"() {
        def failure = new RuntimeException()
        def exclusiveLock = Mock(FileLock)

        when:
        access.open(mode(Shared))

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.close()

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> exclusiveLock
        1 * initializationAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(exclusiveLock) >> { throw failure }
        1 * exclusiveLock.close()
        0 * _._

        and:
        RuntimeException e = thrown()
        e == failure
    }

    def "initializes cache on open when lock mode is none"() {
        def action = Mock(Runnable)
        def contentionAction

        when:
        access.open(mode(None))

        then:
        0 * _._

        when:
        access.useCache("some action", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "some action") >> lock
        1 * lockManager.allowContention(lock, _ as Runnable) >> { FileLock l, Runnable r -> contentionAction = r }
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        1 * action.run()
        _ * lock.mode >> Exclusive
        _ * lock.state
        0 * _._

        when:
        contentionAction.run()

        then:
        1 * lock.close()

        when:
        access.useCache("some action", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "some action") >> lock
        1 * lockManager.allowContention(lock, _ as Runnable) >> { FileLock l, Runnable r -> contentionAction = r }
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        1 * action.run()
        _ * lock.mode >> Exclusive
        _ * lock.state
        0 * _._
    }

    def "does not acquire lock on open when initial lock mode is none"() {
        when:
        access.open(mode(None))

        then:
        0 * _._

        when:
        access.close()

        then:
        0 * _._

        and:
        !access.owner
    }

    @Unroll
    def "cannot be opened more than once for mode #lockMode"() {
        lockManager.lock(lockFile, lockMode, "<display-name>") >> lock

        when:
        access.open(lockMode)
        access.open(lockMode)

        then:
        thrown(IllegalStateException)

        where:
        lockMode << [mode(Shared), mode(Exclusive), mode(None)]
    }

    def "using cache pushes an operation and acquires lock but does not release it at the end of the operation"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "some operation") >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state
        1 * lockManager.allowContention(lock, _ as Runnable)

        then:
        1 * action.create() >> {
            assert access.owner == Thread.currentThread()
        }

        then:
        1 * lock.getMode() >> Exclusive
        0 * _._

        and:
        !access.owner
    }

    def "nested use cache operation does not release the lock"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> lock
        1 * action.create() >> {
            access.useCache("nested operation") {
                assert access.owner == Thread.currentThread()
            }
        }

        then:
        !access.owner
    }

    def "use cache operation reuses existing file lock"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "some operation") >> lock
        1 * action.create() >> { assert access.owner == Thread.currentThread() }

        when:
        access.useCache("some other operation", action)

        then:
        0 * lockManager._
        1 * action.create() >> { assert access.owner == Thread.currentThread() }
        0 * _._

        and:
        !access.owner
    }

    def "use cache operation does not allow shared locks"() {
        given:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock
        access.open(mode(Shared))

        when:
        access.useCache("some operation", Mock(Factory))

        then:
        thrown(UnsupportedOperationException)
    }

    def "long running operation pushes an operation and releases ownership but not lock"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock
        access.owner == Thread.currentThread()

        when:
        access.longRunningOperation("some operation", action)

        then:
        _ * lock.mode >> Exclusive
        0 * lock._

        then:
        1 * action.create() >> {
            assert !access.owner
        }

        then:
        0 * _._

        then:
        access.owner == Thread.currentThread()
    }

    def "long running operation closes the lock if contended during action"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock

        when:
        access.longRunningOperation("some operation", action)

        then:
        1 * action.create() >> {
            access.whenContended().run()
        }
        1 * lock.close()
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> lock
    }

    def "long running operation closes the lock if contended before action"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(Exclusive))

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock

        when:
        access.whenContended().run()
        access.longRunningOperation("some operation", action)

        then:
        1 * action.create()
        1 * lock.close()
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> lock
    }

    def "top-level long running operation does not lock file"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.longRunningOperation("some operation", action)

        then:
        1 * action.create() >> {
            assert !access.owner
        }

        then:
        0 * lock._
        0 * lockManager._
    }

    def "re-entrant long running operation does not lock file"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.longRunningOperation("some operation", action)

        then:
        1 * action.create() >> {
            access.longRunningOperation("other operation") {
                assert !access.owner
            }
        }

        then:
        0 * lock._
        0 * lockManager._
    }

    def "can create new cache"() {
        when:
        def cache = access.newCache(new PersistentIndexedCacheParameters('cache', String.class, Integer.class))

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "contended action does nothing when no lock"() {
        when:
        access.whenContended().run()

        then:
        0 * _._
    }

    def "contended action safely closes the lock when cache is not busy"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> lock

        when:
        access.whenContended().run()

        then:
        1 * lock.close()
    }

    def "file access requires acquired lock"() {
        def runnable = Mock(Runnable)

        when:
        access.open(mode(None))
        access.fileAccess.updateFile(runnable)

        then:
        thrown(IllegalStateException)
    }

    def "file access is available when lock is acquired"() {
        def runnable = Mock(Runnable)

        when:
        access.open(mode(Exclusive))
        access.fileAccess.updateFile(runnable)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>") >> lock
        1 * lock.updateFile(runnable)
    }

    def "file access is available when there is an owner"() {
        def runnable = Mock(Runnable)

        when:
        access.open(mode(None))
        access.useCache("use cache", { access.fileAccess.updateFile(runnable)})

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "use cache") >> lock
        1 * lock.updateFile(runnable)
    }

    def "file access can not be accessed when there is no owner"() {
        def runnable = Mock(Runnable)

        given:
        lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "use cache") >> lock
        access.open(mode(None))
        access.useCache("use cache", runnable)

        when:
        access.fileAccess.updateFile(runnable)

        then:
        thrown(IllegalStateException)
    }

    def "can close cache when the cache has not been used"() {
        when:
        access.open(mode(None))
        access.close()

        then:
        0 * _
    }

    def "can close cache when there is no owner"() {
        given:
        lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "use cache") >> lock
        lock.writeFile(_) >> { Runnable r -> r.run() }
        access.open(mode(None))
        def cache = access.newCache(new PersistentIndexedCacheParameters('cache', String.class, Integer.class))
        access.useCache("use cache", { cache.get("key") })

        when:
        access.close()

        then:
        1 * lock.close()
    }

}