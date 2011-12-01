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
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    final UnitOfWorkCacheManager manager = new UnitOfWorkCacheManager("<display-name>", lockFile, lockManager) {
        @Override
        def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
            return backingCache
        }
    }

    def "can create cache instance outside of unit of work"() {
        when:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "can create cache instance inside of unit of work"() {
        given:
        manager.onStartWork()

        when:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "locks cache dir on first access after start of unit of work"() {
        when:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)
        manager.onStartWork("<some-operation>")

        then:
        0 * _._

        when:
        cache.get("key")
        cache.put("key", 12)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "<some-operation>") >> lock
        _ * backingCache._
        _ * lock.writeToFile(!null) >> {Runnable action -> action.run() }
        _ * lock.readFromFile(!null) >> {Factory action -> action.create() }
        0 * _._
    }

    def "closes caches and unlocks cache dir at end of unit of work"() {
        given:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)
        manager.onStartWork("<some-operation>")
        cacheOpenedAndLocked(cache)

        when:
        manager.onEndWork()

        then:
        1 * lock.writeToFile(!null) >> {Runnable action -> action.run()}
        1 * backingCache.close()

        and:
        1 * lock.close()
        0 * _._
    }

    def "does nothing if no caches used during unit of work"() {
        given:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        when:
        manager.onStartWork("<some-operation>")
        manager.onEndWork()

        then:
        0 * _._
    }

    def "cannot use cache before unit of work started"() {
        given:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        when:
        cache.get("5")

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use cache outside a unit of work.'
    }

    def "cannot use cache after unit of work completed"() {
        given:
        def cache = manager.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)
        manager.onStartWork("<some-operation>")
        cacheOpenedAndLocked(cache)
        manager.onEndWork()

        when:
        cache.get("5")

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use cache outside a unit of work.'
    }

    def expectLockUsed() {
        _ * lock.writeToFile(!null) >> {Runnable action -> action.run() }
        _ * lock.readFromFile(!null) >> {Factory action -> action.create() }
    }

    def cacheOpenedAndLocked(def cache) {
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", !null) >> lock
        expectLockUsed()

        cache.put("key", 12)
    }
}
