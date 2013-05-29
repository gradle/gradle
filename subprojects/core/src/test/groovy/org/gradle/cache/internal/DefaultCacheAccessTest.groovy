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

import org.gradle.api.Action
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.internal.Factory
import org.gradle.messaging.serialize.Serializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

import static org.gradle.cache.internal.FileLockManager.LockMode.*

@Ignore //TODO SF fix & enable
class DefaultCacheAccessTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final FileLockManager lockManager = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final File targetFile = tmpDir.file('cache.bin')
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()
    final DefaultCacheAccess access = new DefaultCacheAccess("<display-name>", lockFile, lockManager) {
        @Override
        def <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
            return backingCache
        }
    }

    def "acquires lock on open and releases on close when initial lock mode is not none"() {
        when:
        access.open(Shared)

        then:
        1 * lockManager.lock(lockFile, Shared, "<display-name>", _ as Action) >> lock
        0 * _._

        when:
        access.close()

        then:
        1 * lock.close()
        0 * _._
    }

    def "does not acquire lock on open when initial lock mode is none"() {
        when:
        access.open(None)

        then:
        0 * _._

        when:
        access.close()

        then:
        0 * _._
    }

    def "acquires lock at the start of the cache action and marks lock as busy at the end of the cache action when initial lock mode is none"() {
        Factory<String> action = Mock()

        given:
        access.open(None)

        when:
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation", _ as Action) >> lock

        and:
        1 * action.create()

        and:
        1 * lock.isContended() >> false
        1 * lock.getMode() >> Exclusive
        1 * lock.setBusy(false)
        0 * _._
    }

    def "does not acquire lock at start of cache action when initial lock mode is exclusive"() {
        Factory<String> action = Mock()

        given:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", _ as Action) >> lock
        access.open(Exclusive)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
        }
        _ * lock.readFile(_)
        _ * lock.writeFile(_)

        and:
        0 * _._
    }

    def "can create cache instance outside of cache action"() {
        given:
        access.open(None)

        when:
        def cache = access.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache
        0 * _._
    }

    def "can create cache instance inside of cache action"() {
        def cache

        given:
        access.open(None)

        when:
        access.useCache("init", {
            cache = access.newCache(tmpDir.file('cache.bin'), String.class, Integer.class)
        } as Factory)

        then:
        cache instanceof MultiProcessSafePersistentIndexedCache

        and:
        1 * lockManager.lock(lockFile, Exclusive, _, _, _ as Action) >> lock
    }

    def "can use cache instance during cache action"() {
        Factory<String> action = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
        }
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation", _ as Action) >> lock
        _ * lock.readFile(_)

        and:
        _ * lock.writeFile(_)
        1 * lock.contended >> true
        1 * lock.close()
        0 * _._
    }

    def "releases lock before long running operation and reacquires after"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
            access.longRunningOperation("nested", longRunningAction)
            canAccess cache
        }
        1 * longRunningAction.create()
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation", _ as Action) >> lock
        _ * lock.readFile(_)
        _ * lock.writeFile(_)
        2 * lock.isContended() >> true
        1 * lock.getMode() >> Exclusive
        2 * lock.close()
        0 * _._
    }

    def "releases lock before nested long running operation and reacquires after"() {
        Factory<String> action = Mock()
        Factory<String> lockInsideLongRunningOperation = Mock()
        Factory<String> nestedLongRunningAction = Mock()
        Factory<String> deeplyNestedAction = Mock()

        FileLock anotherLock = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
            access.longRunningOperation("nested", lockInsideLongRunningOperation)
            canAccess cache
        }
        1 * lockInsideLongRunningOperation.create() >> {
            cannotAccess cache
            access.useCache("nested operation", nestedLongRunningAction)
            cannotAccess cache
        }
        1 * nestedLongRunningAction.create() >> {
            canAccess cache
            access.longRunningOperation("nested-2", deeplyNestedAction)
            canAccess cache
        }
        1 * deeplyNestedAction.create() >> {
            cannotAccess cache
        }

        2 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation", _ as Action) >> lock
        4 * lock.isContended() >> true
        3 * lock.getMode() >> Exclusive
        _ * lock.readFile(_)
        _ * lock.writeFile(_)
        4 * lock.close()

        2 * lockManager.lock(lockFile, Exclusive, "<display-name>", "nested operation", _ as Action) >> anotherLock
        2 * anotherLock.isContended() >> true
        3 * anotherLock.getMode() >> Exclusive
        _ * anotherLock.readFile(_)
        _ * anotherLock.writeFile(_)
        2 * anotherLock.close()
        0 * _._
    }

    def "cannot run long running operation from outside cache action"() {
        given:
        access.open(None)

        when:
        access.longRunningOperation("operation", Mock(Factory))

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot start long running operation, as the <display-name> has not been locked.'
    }

    def "cannot use cache from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        _ * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        1 * action.create() >> {
            access.longRunningOperation("nested", longRunningAction)
        }
        1 * longRunningAction.create() >> {
            cannotAccess cache
        }
    }

    def "can execute cache action from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock

        and:
        1 * action.create() >> {
            canAccess cache
            access.longRunningOperation("nested", longRunningAction)
            canAccess cache
        }

        and:
        1 * lock.close()

        and:
        1 * longRunningAction.create() >> {
            cannotAccess cache
            access.useCache("nested 2", nestedAction)
            cannotAccess cache
        }

        and:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "nested 2") >> lock

        and:
        1 * nestedAction.create() >> {
            canAccess cache
        }

        and:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFile(_)
        _ * lock.writeFile(_)
        2 * lock.close()
        0 * _._
    }

    def "can execute long running operation from within long running operation"() {
        Factory<String> action = Mock()
        Factory<String> longRunningAction = Mock()
        Factory<String> nestedAction = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
            access.longRunningOperation("nested", longRunningAction)
            canAccess cache
        }
        1 * longRunningAction.create() >> {
            cannotAccess cache
            access.longRunningOperation("nested 2", nestedAction)
            cannotAccess cache
        }
        1 * nestedAction.create() >> {
            cannotAccess cache
        }
        2 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFile(_)
        _ * lock.writeFile(_)
        2 * lock.close()
        0 * _._
    }

    def "can execute cache action from within cache action"() {
        Factory<String> action = Mock()
        Factory<String> nestedAction = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
            access.useCache("nested", nestedAction)
            canAccess cache
        }
        1 * nestedAction.create() >> {
            canAccess cache
        }
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFile(_)
        _ * lock.writeFile(_)
        1 * lock.close()
        0 * _._
    }

    def "closes caches at the end of the cache action when initial lock mode is none"() {
        Factory<String> action = Mock()

        given:
        access.open(None)
        def cache = access.newCache(targetFile, String, Integer)

        when:
        access.useCache("some operation", action)

        then:
        1 * action.create() >> {
            canAccess cache
        }
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>", "some operation") >> lock
        _ * lock.readFile(_)

        and:
        _ * lock.writeFile(_) >> {Runnable runnable -> runnable.run()}
        1 * backingCache.close()
        1 * lock.close()
        0 * _._
    }

    def "closes caches on close when initial lock mode is not none"() {
        given:
        1 * lockManager.lock(lockFile, Exclusive, "<display-name>") >> lock
        _ * lock.readFile(_) >> {Factory factory -> factory.create()}
        _ * lock.writeFile(_) >> {Runnable runnable -> runnable.run()}

        and:
        access.open(Exclusive)
        def cache = access.newCache(targetFile, String, Integer)
        cache.get("key")

        when:
        access.close()

        then:
        _ * lock.readFile(_) >> {Factory factory -> factory.create()}
        _ * lock.writeFile(_) >> {Runnable runnable -> runnable.run()}
        1 * backingCache.close()
        1 * lock.close()
        0 * _._
    }

    def canAccess(def cache) {
        try {
            cache.get("key")
        } catch (IllegalStateException e) {
            assert false: "Should be able to access cache here"
        }
    }

    def cannotAccess(def cache) {
        try {
            cache.get("key")
            assert false: "Should not be able to access cache here"
        } catch (IllegalStateException e) {
            assert e.message == 'The <display-name> has not been locked. File lock available: false'
        }
    }

}
