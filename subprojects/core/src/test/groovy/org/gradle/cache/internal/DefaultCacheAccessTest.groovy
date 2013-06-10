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
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack
import org.gradle.internal.Factory
import org.gradle.messaging.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.FileLockManager.LockMode.*

class DefaultCacheAccessTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final FileLockManager lockManager = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final File targetFile = tmpDir.file('cache.bin')
    final FileLock lock = Mock()
    final CacheAccessOperationsStack operations = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    DefaultCacheAccess access = newAccess(operations)

    private DefaultCacheAccess newAccess(CacheAccessOperationsStack operations) {
        new DefaultCacheAccess("<display-name>", lockFile, lockManager, operations) {
            @Override
            def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                return backingCache
            }
        }
    }

    def "acquires lock on open and releases on close when initial lock mode is not none"() {
        when:
        access.open(Shared)

        then:
        1 * lockManager.lock(lockFile, Shared, "<display-name>", _) >> lock
        1 * operations.pushCacheAction("Access <display-name>")
        0 * _._

        and:
        access.owner

        when:
        access.close()

        then:
        1 * lock.close()
        1 * operations.close()
        0 * _._

        and:
        !access.owner
    }

    def "lock cannot be acquired more than once when initial lock mode is not none"() {
        lockManager.lock(lockFile, Shared, "<display-name>", _) >> lock

        when:
        access.open(Shared)
        access.open(Shared)

        then:
        thrown(IllegalStateException)
    }

    def "does not acquire lock on open when initial lock mode is none"() {
        when:
        access.open(None)

        then:
        0 * _._

        when:
        access.close()

        then:
        1 * operations.close()
        0 * _._

        and:
        !access.owner
    }

    def "using cache pushes an operation and acquires ownership"() {
        Factory<String> action = Mock()

        when:
        access.useCache("some operation", action)

        then:
        1 * operations.pushCacheAction("some operation")

        then:
        1 * operations.description >> "some operation"
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation", _) >> lock

        then:
        1 * action.create() >> {
            assert access.owner
        }

        then:
        1 * lock.getMode() >> Exclusive
        1 * operations.inCacheAction >> false
        1 * operations.popCacheAction("some operation")
        0 * _._

        and:
        !access.owner
    }

    def "nested use cache operation does not release the ownership"() {
        Factory<String> action = Mock()

        when:
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _, _) >> lock
        1 * action.create()
        1 * operations.inCacheAction >> true

        then:
        access.owner
    }

    def "use cache operation reuses existing file lock"() {
        Factory<String> action = Mock()

        when:
        access.open(Exclusive)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _) >> lock

        when:
        access.useCache("some operation", action)

        then:
        0 * lockManager._
        1 * action.create()
    }

    def "use cache operation does not allow shared locks"() {
        access.open(Shared)

        when:
        access.useCache("some operation", Mock(Factory))

        then:
        thrown(UnsupportedOperationException)
    }

    def "long running operation fails early when there is no lock"() {
        when:
        access.longRunningOperation("some operation", Mock(Factory))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start long running operation, as the <display-name> has not been locked.'
    }

    def "long running operation pushes an operation and releases ownership"() {
        lock.mode >> Exclusive
        Factory<String> action = Mock()

        when:
        access.open(Exclusive)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _) >> lock
        assert access.owner

        when:
        access.longRunningOperation("some operation", action)

        then:
        1 * operations.maybeReentrantLongRunningOperation("some operation") >> false

        then:
        0 * lock.close()
        1 * operations.pushLongRunningOperation("some operation")

        then:
        1 * action.create() >> {
            assert !access.owner
        }

        then:
        0 * lockManager._
        1 * operations.popLongRunningOperation("some operation")

        then:
        access.owner
    }

    def "long running operation closes the lock if contended"() {
        Factory<String> action = Mock()

        when:
        access.open(Exclusive)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _) >> lock

        when:
        access.whenContended().run()
        access.longRunningOperation("some operation", action)

        then:
        1 * lock.close()

        then:
        1 * action.create()

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _, _)
    }

    def "long running operation closes the lock if the lock is shared"() {
        Factory<String> action = Mock()

        when:
        access.open(Shared)

        then:
        1 * lockManager.lock(lockFile, Shared, "<display-name>", _) >> lock

        when:
        access.longRunningOperation("some operation", action)

        then:
        1 * lock.mode >> Shared
        1 * lock.close()

        then:
        1 * action.create()

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _, _)
    }

    def "reentrant long running operation does not involve locking"() {
        Factory<String> action = Mock()

        when:
        access.longRunningOperation("some operation", action)

        then:
        1 * operations.maybeReentrantLongRunningOperation("some operation") >> true

        then:
        1 * action.create()

        then:
        1 * operations.popLongRunningOperation("some operation")
        0 * lock._
        0 * lockManager._
    }

    def "can create new cache"() {
        when:
        def cache = access.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

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
        access.open(Exclusive)
        access.longRunningOperation("some operation", action)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _) >> lock
        action.create() >> {
            access.whenContended().run()
        }

        and:
        1 * operations.pushCacheAction('Other process requested access to <display-name>')
        1 * lock.close()
        1 * operations.popCacheAction('Other process requested access to <display-name>')
    }
}