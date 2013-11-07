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

import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.internal.Factory
import org.gradle.messaging.serialize.Serializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.cache.internal.FileLockManager.LockMode.*
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultCacheAccessTest extends ConcurrentSpec {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final FileLockManager lockManager = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    DefaultCacheAccess access = newAccess()

    private DefaultCacheAccess newAccess() {
        new DefaultCacheAccess("<display-name>", lockFile, lockManager) {
            @Override
            def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                return backingCache
            }
        }
    }

    def "acquires lock on open and releases on close when initial lock mode is not none"() {
        when:
        access.open(mode(Shared))

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock
        1 * lockManager.allowContention(lock, _ as Runnable)
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

    def "lock cannot be acquired more than once when initial lock mode is not none"() {
        lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock

        when:
        access.open(mode(Shared))
        access.open(mode(Shared))

        then:
        thrown(IllegalStateException)
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

    def "using cache pushes an operation and acquires lock but does not release it at the end of the operation"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(None))
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", "some operation") >> lock
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

    def "long running operation closes the lock if contended"() {
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

    def "long running operation closes the lock if the lock is shared"() {
        Factory<String> action = Mock()

        when:
        access.open(mode(Shared))

        then:
        1 * lockManager.lock(lockFile, mode(Shared), "<display-name>") >> lock

        when:
        access.longRunningOperation("some operation", action)

        then:
        1 * lock.mode >> Shared
        1 * lock.close()

        then:
        1 * action.create()

        then:
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
        def cache = access.newCache(new PersistentIndexedCacheParameters(tmpDir.file('cache.bin'), String.class, Integer.class))

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

    def "file access can be accessed when there is no owner"() {
        def runnable = Mock(Runnable)

        when:
        access.open(mode(None))
        access.useCache("use cache", {} as Runnable) //acquires file lock but releases the thread lock
        access.fileAccess.updateFile(runnable)

        then:
        1 * lockManager.lock(lockFile, mode(Exclusive), "<display-name>", _) >> lock
        1 * lock.updateFile(runnable)
    }
}