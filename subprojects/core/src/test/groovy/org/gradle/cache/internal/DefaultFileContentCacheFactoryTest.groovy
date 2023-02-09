/*
 * Copyright 2017 the original author or authors.
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


import org.gradle.cache.AsyncCacheAccess
import org.gradle.cache.CacheDecorator
import org.gradle.cache.CrossProcessCacheAccess
import org.gradle.cache.MultiProcessSafeIndexedCache
import org.gradle.cache.internal.scopes.DefaultCacheScopeMapping
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.gradle.util.GradleVersion
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable

@UsesNativeServices
class DefaultFileContentCacheFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def listenerManager = new DefaultListenerManager(Scopes.Build)
    def fileSystemAccess = Mock(FileSystemAccess)
    def cachesDir = tmpDir.file("caches")
    def cacheScopeMapping = new DefaultCacheScopeMapping(cachesDir, GradleVersion.current())
    def cacheRepository = new DefaultUnscopedCacheBuilderFactory(cacheScopeMapping, new TestInMemoryCacheFactory())
    def globalScopedCache = new DefaultGlobalScopedCacheBuilderFactory(cachesDir, cacheRepository)
    def inMemoryTaskArtifactCache = new DefaultInMemoryCacheDecoratorFactory(false, new TestCrossBuildInMemoryCacheFactory()) {
        @Override
        CacheDecorator decorator(int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
            return new CacheDecorator() {
                @Override
                public <K, V> MultiProcessSafeIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafeIndexedCache<K, V> indexedCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
                    return indexedCache
                }
            }
        }
    }
    def factory = new DefaultFileContentCacheFactory(listenerManager, fileSystemAccess, globalScopedCache, inMemoryTaskArtifactCache)
    def calculator = Mock(FileContentCacheFactory.Calculator)

    def "calculates entry value for file when not seen before and reuses result"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        1 * calculator.calculate(file, true) >> 12
        0 * _

        when:
        result = cache.get(file)

        then:
        result == 12
        0 * _
    }

    def "calculates entry value for directory when not seen before and reuses result"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        1 * fileSystemAccess.readRegularFileContentHash(file.absolutePath) >> Optional.empty()
        1 * calculator.calculate(file, false) >> 12
        0 * _

        when:
        result = cache.get(file)

        then:
        result == 12
        0 * _
    }

    def "reuses calculated value for file across cache instances"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        1 * calculator.calculate(file, true) >> 12
        0 * _

        when:
        result = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER).get(file)

        then:
        result == 12

        and:
        0 * _
    }

    def "reuses calculated value for file across factory instances"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        1 * calculator.calculate(file, true) >> 12
        0 * _

        when:
        def otherFactory = new DefaultFileContentCacheFactory(listenerManager, fileSystemAccess, globalScopedCache, inMemoryTaskArtifactCache)
        result = otherFactory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER).get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        0 * _
    }

    def "reuses result when file content has not changed after task outputs may have changed"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        1 * calculator.calculate(file, true) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(OutputChangeListener).invalidateCachesFor([])
        result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file)
        }
        0 * _
    }

    def "calculates result for directory content after task outputs may have changed"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        1 * fileSystemAccess.readRegularFileContentHash(file.getAbsolutePath()) >> Optional.empty()
        1 * calculator.calculate(file, false) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(OutputChangeListener).invalidateCachesFor([])
        result = cache.get(file)

        then:
        result == 10

        and:
        1 * fileSystemAccess.readRegularFileContentHash(file.getAbsolutePath()) >> Optional.empty()
        1 * calculator.calculate(file, false) >> 10
        0 * _
    }

    def "calculates result when file content has changed"() {
        def file = new File("thing.txt")
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12

        and:
        interaction {
            snapshotRegularFile(file, TestHashCodes.hashCodeFrom(123))
        }
        1 * calculator.calculate(file, true) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(OutputChangeListener).invalidateCachesFor([])
        result = cache.get(file)

        then:
        result == 10

        and:
        interaction {
            snapshotRegularFile(file, TestHashCodes.hashCodeFrom(321))
        }
        1 * calculator.calculate(file, true) >> 10
        0 * _
    }

    def snapshotRegularFile(File file, @Nullable HashCode hashCode = TestHashCodes.hashCodeFrom(123)) {
        1 * fileSystemAccess.readRegularFileContentHash(file.getAbsolutePath()) >> { location ->
            return Optional.ofNullable(hashCode)
        }
    }
}
