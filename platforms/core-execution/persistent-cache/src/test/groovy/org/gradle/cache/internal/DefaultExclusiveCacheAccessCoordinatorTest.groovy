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
import org.gradle.cache.AsyncCacheAccess
import org.gradle.cache.CacheDecorator
import org.gradle.cache.CrossProcessCacheAccess
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.FileLockReleasedSignal
import org.gradle.cache.LockOptions
import org.gradle.cache.MultiProcessSafeIndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.internal.Factory
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Serializer
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.None
import static org.gradle.cache.FileLockManager.LockMode.OnDemand
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultExclusiveCacheAccessCoordinatorTest extends ConcurrentSpec {
    private static final BaseSerializerFactory SERIALIZER_FACTORY = new BaseSerializerFactory()

    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final FileLockManager lockManager = Mock()
    final CacheInitializationAction initializationAction = Mock()
    final CacheCleanupExecutor cleanupExecutor = Mock()
    final File lockFile = tmpDir.file('lock.bin')
    final File cacheDir = tmpDir.file('caches')
    final FileLock lock = Mock()
    final BTreePersistentIndexedCache<String, Integer> backingCache = Mock()

    private DefaultCacheCoordinator newAccess(FileLockManager.LockMode lockMode) {
        new DefaultCacheCoordinator("<display-name>", lockFile, new LockOptionsBuilder(lockMode), cacheDir, lockManager, initializationAction, cleanupExecutor, executorFactory) {
            @Override
            <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                return backingCache
            }
        }
    }

    def "acquires lock on open and releases on close when lock mode is shared"() {
        def access = newAccess(Shared)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state
        0 * _._

        when:
        access.close()

        then:
        _ * lock.state
        1 * cleanupExecutor.cleanup()
        1 * lock.close()
        0 * _._
    }

    def "acquires lock on open and releases on close when lock mode is exclusive"() {
        def access = newAccess(Exclusive)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state
        0 * _._

        when:
        access.close()

        then:
        _ * lock.state
        1 * cleanupExecutor.cleanup()
        1 * lock.close()
        0 * _._
    }

    def "cleans up using cleanup executor with access #accessType"() {
        def access = newAccess(accessType)
        when:
        access.cleanup()

        then:
        _ * lock.state
        1 * cleanupExecutor.cleanup()
        0 * _._

        where:
        accessType << [Exclusive, Shared, OnDemand, None]
    }

    def "cleans up on close when clean up has not already occurred"() {
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.close()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _
    }

    def "does not clean up on close when clean up has already occurred"() {
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.cleanup()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _

        when:
        access.close()

        then:
        0 * _
    }

    def "can explicitly clean up multiple times"() {
        def access = newAccess(OnDemand)

        when:
        access.cleanup()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _

        when:
        access.cleanup()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _
    }

    def "initializes cache on open when lock mode is shared by upgrading lock"() {
        def exclusiveLock = Mock(FileLock)
        def sharedLock = Mock(FileLock)
        def access = newAccess(Shared)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.close()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>") >> exclusiveLock
        1 * initializationAction.requiresInitialization(exclusiveLock) >> true
        1 * exclusiveLock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(exclusiveLock)
        1 * exclusiveLock.close()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Shared), "<display-name>") >> sharedLock
        1 * initializationAction.requiresInitialization(sharedLock) >> false
        _ * sharedLock.state
        0 * _._
    }

    def "initializes cache on open when lock mode is exclusive"() {
        def access = newAccess(Exclusive)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        _ * lock.state
        0 * _._
    }

    def "cleans up when cache validation fails"() {
        def failure = new RuntimeException()
        def access = newAccess(Exclusive)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
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
        def access = newAccess(Shared)

        when:
        access.open()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Shared), "<display-name>") >> lock
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.close()

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>") >> exclusiveLock
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
        def access = newAccess(OnDemand)

        def contentionAction

        when:
        access.open()

        then:
        0 * _._

        when:
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Action<FileLockReleasedSignal> whenContended -> contentionAction = whenContended; return lock }
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        1 * action.run()
        _ * lock.mode >> Exclusive
        _ * lock.state
        0 * _._

        when:
        contentionAction.execute({} as FileLockReleasedSignal)

        then:
        1 * lock.close()

        when:
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Action<FileLockReleasedSignal> whenContended -> contentionAction = whenContended; return lock }
        1 * initializationAction.requiresInitialization(lock) >> true
        1 * lock.writeFile(_) >> { Runnable r -> r.run() }
        1 * initializationAction.initialize(lock)
        1 * action.run()
        _ * lock.mode >> Exclusive
        _ * lock.state
        0 * _._
    }

    def "does not acquire lock on open when initial lock mode is none"() {
        def access = newAccess(OnDemand)

        when:
        access.open()

        then:
        0 * _._

        when:
        access.close()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _._

        and:
        !access.owner
    }

    def "cannot be opened more than once for mode #lockMode"() {
        if (lockMode == Shared) {
            lockManager.lock(lockFile, _, "<display-name>") >> lock
        } else {
            lockManager.lock(lockFile, _, "<display-name>", "", _) >> lock
        }
        def access = newAccess(lockMode)

        when:
        access.open()
        access.open()

        then:
        thrown(IllegalStateException)

        where:
        lockMode << [Shared, Exclusive, OnDemand]
    }

    def "with file lock operation acquires lock but does not release it at the end of the operation"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.withFileLock(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.getState()

        then:
        1 * action.create() >> "result"

        then:
        0 * _

        and:
        !access.owner
    }

    def "with file lock operation reuses existing file lock"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.withFileLock(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.getState()
        1 * action.create() >> "result"
        0 * _

        when:
        access.withFileLock(action)

        then:
        1 * action.create() >> "result"
        0 * _

        and:
        !access.owner
    }

    def "nested with file lock operation does not release the lock"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.withFileLock(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.getState()

        then:
        1 * action.create() >> {
            access.withFileLock() {
                return "result"
            }
        }
        0 * _

        then:
        !access.owner
    }

    def "using cache pushes an operation and acquires lock but does not release it at the end of the operation"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * initializationAction.requiresInitialization(lock) >> false
        _ * lock.state

        then:
        1 * action.create() >> {
            assert access.owner == Thread.currentThread()
        }

        then:
        0 * _._

        and:
        !access.owner
    }

    def "nested use cache operation does not release the lock"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>","", _) >> lock
        1 * action.create() >> {
            access.useCache {
                assert access.owner == Thread.currentThread()
            }
        }

        then:
        !access.owner
    }

    def "use cache operation reuses existing file lock"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * action.create() >> { assert access.owner == Thread.currentThread() }

        when:
        access.useCache(action)

        then:
        0 * lockManager._
        1 * action.create() >> { assert access.owner == Thread.currentThread() }
        0 * _._

        and:
        !access.owner
    }

    def "use cache operation does not allow shared locks"() {
        def access = newAccess(Shared)

        given:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Shared), "<display-name>") >> lock
        access.open()

        when:
        access.useCache(Mock(Factory))

        then:
        thrown(UnsupportedOperationException)
    }

    def "can create new cache"() {
        def access = newAccess(OnDemand)

        when:
        def cache = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))

        then:
        cache instanceof MultiProcessSafeIndexedCache
        0 * _._
    }

    def "contended action safely closes the lock when cache is not busy"() {
        Factory<String> action = Mock()
        def access = newAccess(OnDemand)
        def contendedAction

        when:
        access.open()
        access.useCache(action)

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Action<FileLockReleasedSignal> whenContended -> contendedAction = whenContended; return lock }

        when:
        contendedAction.execute({} as FileLockReleasedSignal)

        then:
        1 * lock.close()
    }

    def "file access requires acquired lock"() {
        def runnable = Mock(Runnable)
        def access = newAccess(mode)

        given:
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock

        when:
        access.open()
        access.fileAccess.updateFile(runnable)

        then:
        thrown(IllegalStateException)

        where:
        mode << [Exclusive, OnDemand]
    }

    def "file access is available when there is an owner"() {
        def runnable = Mock(Runnable)
        def access = newAccess(mode)

        when:
        access.open()
        access.useCache { access.fileAccess.updateFile(runnable)}

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        1 * lock.updateFile(runnable)

        where:
        mode << [Exclusive, OnDemand]
    }

    def "file access can not be accessed when there is no owner"() {
        def runnable = Mock(Runnable)
        def access = newAccess(mode)

        given:
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>") >> lock
        access.open()
        access.useCache(runnable)

        when:
        access.fileAccess.updateFile(runnable)

        then:
        thrown(IllegalStateException)

        where:
        mode << [Exclusive, OnDemand]
    }

    def "can close cache when the cache has not been used"() {
        def access = newAccess(OnDemand)

        when:
        access.open()
        access.close()

        then:
        1 * cleanupExecutor.cleanup()
        0 * _
    }

    def "can close cache when there is no owner"() {
        def access = newAccess(OnDemand)

        given:
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        lock.writeFile(_) >> { Runnable r -> r.run() }
        access.open()
        def cache = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.useCache { cache.getIfPresent("key") }

        when:
        access.close()

        then:
        1 * lock.close()
    }

    def "can close cache when the lock has been released"() {
        def access = newAccess(OnDemand)
        def contendedAction

        given:
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Action<FileLockReleasedSignal> whenContended -> contendedAction = whenContended; return lock }
        lock.writeFile(_) >> { Runnable r -> r.run() }
        access.open()
        def cache = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.useCache { cache.getIfPresent("key") }
        contendedAction.execute({} as FileLockReleasedSignal)
        lock.close()

        when:
        access.close()

        then:
        0 * lock._
    }

    def "releases lock acquired by cache decorator when contended"() {
        def decorator = Mock(CacheDecorator)
        def access = newAccess(OnDemand)
        def contendedAction

        given:
        CrossProcessCacheAccess cpAccess
        decorator.decorate(_, _, _, _, _) >> { String cacheId, String cacheName, MultiProcessSafeIndexedCache indexedCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess ->
            cpAccess = crossProcessCacheAccess
            indexedCache
        }

        access.open()

        when:
        def cache = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class).withCacheDecorator(decorator))

        then:
        1 * lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> {
            File target, LockOptions options, String targetDisplayName, String operationDisplayName, Action<FileLockReleasedSignal> whenContended -> contendedAction = whenContended; return lock }

        when:
        cpAccess.withFileLock {
            access.useCache {
                cache.getIfPresent("something")
            }
            contendedAction.execute({} as FileLockReleasedSignal)
            "result"
        }

        then:
        1 * lock.close()

        cleanup:
        access?.close()
    }

    def "does not acquire file lock for cleanup"() {
        given:
        def access = newAccess(OnDemand)
        access.open()

        when:
        access.cleanup()

        then:
        1 * cleanupExecutor.cleanup()
        0 * lockManager._
    }

    def "releases file lock before running cleanup"() {
        def access = newAccess(OnDemand)

        given:
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        lock.writeFile(_) >> { Runnable r -> r.run() }
        access.open()
        def cache = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.useCache { cache.getIfPresent("key") }

        when:
        access.cleanup()

        then:
        lock.close()

        then:
        1 * cleanupExecutor.cleanup()
    }

    def "returns the same cache object when using same cache parameters"() {
        def access = newAccess(OnDemand)

        when:
        def cache1 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        def cache2 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))

        then:
        cache1 == cache2

        cleanup:
        access?.close()
    }

    def "throws InvalidCacheReuseException when cache value type differs"() {
        def access = newAccess(OnDemand)

        when:
        access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.newCache(IndexedCacheParameters.of('cache', String.class, String.class))

        then:
        thrown(DefaultCacheCoordinator.InvalidCacheReuseException)

        cleanup:
        access?.close()
    }

    def "throws InvalidCacheReuseException when cache key type differs"() {
        def access = newAccess(OnDemand)

        when:
        access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.newCache(IndexedCacheParameters.of('cache', Integer.class, Integer.class))

        then:
        thrown(DefaultCacheCoordinator.InvalidCacheReuseException)

        cleanup:
        access?.close()
    }

    def "throws InvalidCacheReuseException when cache decorator differs"() {
        def access = newAccess(OnDemand)
        def decorator = Mock(CacheDecorator)
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>") >> lock
        decorator.decorate(_, _, _, _, _) >> { String cacheId, String cacheName, MultiProcessSafeIndexedCache indexedCacheche, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess ->
            indexedCacheche
        }

        when:
        access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class).withCacheDecorator(decorator))

        then:
        thrown(DefaultCacheCoordinator.InvalidCacheReuseException)

        cleanup:
        access?.close()
    }

    def "returns the same cache object when cache decorator match"() {
        def access = newAccess(OnDemand)
        def decorator = Mock(CacheDecorator)
        lockManager.lock(lockFile, new LockOptionsBuilder(Exclusive), "<display-name>", "", _) >> lock
        decorator.decorate(_, _, _, _, _) >> { String cacheId, String cacheName, MultiProcessSafeIndexedCache indexedCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess ->
            indexedCache
        }

        when:
        def cache1 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class).withCacheDecorator(decorator))
        def cache2 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class).withCacheDecorator(decorator))

        then:
        noExceptionThrown()
        cache1 == cache2

        cleanup:
        access?.close()
    }

    def "returns the same cache object when using compatible value serializer"() {
        def access = newAccess(OnDemand)

        when:
        def cache1 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        def cache2 = access.newCache(IndexedCacheParameters.of('cache', String.class, SERIALIZER_FACTORY.getSerializerFor(Integer.class)))

        then:
        noExceptionThrown()
        cache1 == cache2

        cleanup:
        access?.close()
    }

    def "returns the same cache object when using compatible key serializer"() {
        def access = newAccess(OnDemand)

        when:
        def cache1 = access.newCache(IndexedCacheParameters.of('cache', String.class, Integer.class))
        def cache2 = access.newCache(IndexedCacheParameters.of('cache', SERIALIZER_FACTORY.getSerializerFor(String.class), SERIALIZER_FACTORY.getSerializerFor(Integer.class)))

        then:
        noExceptionThrown()
        cache1 == cache2

        cleanup:
        access?.close()
    }
}
