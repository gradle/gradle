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

import spock.lang.Specification
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import static org.gradle.cache.internal.FileLockManager.LockMode.*
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.api.internal.Factory

class UnitOfWorkCacheManagerTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final FileLockManager lockManager = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final File targetFile = tmpDir.file('cache.bin')
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    final UnitOfWorkCacheManager manager = new UnitOfWorkCacheManager("<display-name>", lockFile, lockManager) {
        @Override
        def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
            return backingCache
        }
    }

    def "executes cache action and returns result"() {
        Factory<String> action = Mock()

        when:
        def result = manager.useCache("some operation", action)

        then:
        result == 'result'

        and:
        1 * action.create() >> 'result'
        0 * _._
    }

    def "can create cache instance outside of cache action"() {
        when:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "can create cache instance inside of cache action"() {
        def cache
        when:
        manager.useCache("init", {
            cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)
        } as Factory)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "does not acquire lock when no caches used during unit of work"() {
        given:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        when:
        manager.useCache("some operation", {} as Factory)

        then:
        0 * _._
    }

    def "acquires lock when a cache is used and releases lock at the end of the cache action"() {
        Factory<String> action = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
        }
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFromFile(_)

        and:
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }

    def "releases lock during long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            manager.longRunningOperation("nested", longRunningAction)
            cache.get("key")
        }
        1 * longRunningAction.create()
        2 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        2 * lock.close()
        0 * _._
    }

    def "cannot run long running operation from outside cache action"() {
        when:
        manager.longRunningOperation("operation", Mock(Factory))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start long running operation, as the artifact cache has not been locked.'
    }

    def "cannot use cache from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        IllegalStateException e = thrown()
        e.message == 'The <display-name> has not been locked.'

        and:
        1 * action.create() >> {
            manager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            cache.get("key")
        }
        0 * _._
    }

    def "can execute cache action from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            manager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            manager.useCache("nested 2", nestedAction)
        }
        1 * nestedAction.create() >> {
            cache.get("key")
        }
        2 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        2 * lock.close()
        0 * _._
    }

    def "can execute long running operation from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            manager.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            manager.longRunningOperation("nested 2", nestedAction)
        }
        1 * nestedAction.create()
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }

    def "can execute cache action from within cache action"() {
        Factory<String> action = Mock()
        Factory<String> nestedAction = Mock()
        def cache = manager.newCache(targetFile, String, Integer)

        when:
        manager.useCache("some operation", action)

        then:
        1 * action.create() >> {
            cache.get("key")
            manager.useCache("nested", nestedAction)
        }
        1 * nestedAction.create() >> {
            cache.get("key")
        }
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFromFile(_)
        _ * lock.writeToFile(_)
        1 * lock.close()
        0 * _._
    }
}
