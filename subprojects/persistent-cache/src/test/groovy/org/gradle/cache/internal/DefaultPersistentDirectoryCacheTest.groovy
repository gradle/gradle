/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheCleanupStrategy
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.locklistener.NoOpFileLockContentionHandler
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GUtil

import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.createDefaultFileLockManager
import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.unlockUncleanly
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultPersistentDirectoryCacheTest extends AbstractProjectBuilderSpec {
    def metaDataProvider = Stub(ProcessMetaDataProvider) {
        getProcessDisplayName() >> "gradle"
        getProcessIdentifier() >> "id"
    }
    def lockManager = new DefaultFileLockManager(metaDataProvider, new NoOpFileLockContentionHandler())
    def initializationAction = Mock(Action)
    def cacheCleanup = Stub(CacheCleanupStrategy)
    def progressLoggerFactory = Stub(ProgressLoggerFactory)

    def properties = ['prop': 'value', 'prop2': 'other-value']

    def initialisesCacheWhenCacheDirDoesNotExist() {
        given:
        def emptyDir = temporaryFolder.getTestDirectory().file("dir")

        expect:
        emptyDir.assertDoesNotExist()

        when:
        def cache = new DefaultPersistentDirectoryCache(emptyDir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(emptyDir.file("cache.properties")) == properties
    }

    def initializesCacheWhenPropertiesFileDoesNotExist() {
        given:
        def dir = temporaryFolder.getTestDirectory().file("dir").createDir()
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenPropertiesHaveChanged() {
        given:
        def dir = createCacheDir(prop: "other-value")
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenPropertyIsAdded() {
        given:
        def dir = createCacheDir()
        def properties = properties + [newProp: 'newValue']
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def rebuildsCacheWhenInitializerFailedOnPreviousOpen() {
        given:
        def dir = temporaryFolder.getTestDirectory().file("dir").createDir()
        final RuntimeException failure = new RuntimeException()
        Action<PersistentCache> failingAction = Stub(Action) {
            execute(_ as PersistentCache) >> { throw failure }
        }
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), failingAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        RuntimeException e = thrown()
        e.cause.is(failure)

        when:
        cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        1 * initializationAction.execute(_ as PersistentCache)
        0 * _
        loadProperties(dir.file("cache.properties")) == properties
    }

    def doesNotInitializeCacheWhenCacheDirExistsAndIsNotInvalid() {
        given:
        def dir = createCacheDir()
        def cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), initializationAction, cacheCleanup, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        when:
        try {
            cache.open()
        } finally {
            cache.close()
        }

        then:
        0 * _  // Does not call initialization action.
        dir.file("cache.properties").isFile()
        dir.file("some-file").isFile()
    }

    def "will rebuild cache if not unlocked cleanly"() {
        given:
        def dir = temporaryFolder.testDirectory.createDir("cache")
        def initialized = false
        def init = { initialized = true } as Action
        def cache = new DefaultPersistentDirectoryCache(dir, "test", [:], CacheBuilder.LockTarget.DefaultTarget,
            mode(FileLockManager.LockMode.Exclusive), init, cacheCleanup, createDefaultFileLockManager(), Mock(ExecutorFactory), progressLoggerFactory)

        when:
        unlockUncleanly(dir.file("cache.properties"))
        cache.open()

        then:
        initialized

        cleanup:
        cache.close()
    }

    def "will rebuild cache if cache.properties is missing and properties are not empty"() {
        given:
        def dir = createCacheDir()
        def initialized = false
        def init = { initialized = true } as Action
        def properties = [foo: 'bar']
        def cache = new DefaultPersistentDirectoryCache(dir, "test", properties, CacheBuilder.LockTarget.DefaultTarget,
            mode(FileLockManager.LockMode.Exclusive), init, cacheCleanup, createDefaultFileLockManager(), Mock(ExecutorFactory), progressLoggerFactory)

        when:
        dir.file("cache.properties").delete()
        cache.open()

        then:
        initialized

        cleanup:
        cache.close()
    }

    def "will not rebuild cache if cache.properties is missing but properties are empty"() {
        given:
        def dir = createCacheDir()
        def initialized = false
        def init = { initialized = true } as Action
        def properties = [:]
        def cache = new DefaultPersistentDirectoryCache(dir, "test", properties, CacheBuilder.LockTarget.DefaultTarget,
            mode(FileLockManager.LockMode.Exclusive), init, cacheCleanup, createDefaultFileLockManager(), Mock(ExecutorFactory), progressLoggerFactory)

        when:
        dir.file("cache.properties").delete()
        cache.open()

        then:
        !initialized

        cleanup:
        cache.close()
    }

    private static Map<String, String> loadProperties(TestFile file) {
        Properties properties = GUtil.loadProperties(file)
        Map<String, String> result = new HashMap<String, String>()
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString())
        }
        return result
    }

    private TestFile createCacheDir(Map<String, ?> extraProps = [:]) {
        def dir = temporaryFolder.getTestDirectory()

        Map<String, Object> properties = new HashMap<String, Object>()
        properties.putAll(this.properties)
        properties.putAll(extraProps)

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, "<display-name>", properties, CacheBuilder.LockTarget.DefaultTarget, mode(FileLockManager.LockMode.Shared), null, null, lockManager, Mock(ExecutorFactory), progressLoggerFactory)

        try {
            cache.open()
            dir.file("some-file").touch()
        } finally {
            cache.close()
        }

        return dir
    }
}
