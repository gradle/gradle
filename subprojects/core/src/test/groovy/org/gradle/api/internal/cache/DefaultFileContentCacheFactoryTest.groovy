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

package org.gradle.api.internal.cache

import com.google.common.hash.HashCode
import org.gradle.api.internal.changedetection.state.FileSystemMirror
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache
import org.gradle.api.internal.hash.FileHasher
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener
import org.gradle.api.invocation.Gradle
import org.gradle.cache.internal.AsyncCacheAccess
import org.gradle.cache.internal.CacheDecorator
import org.gradle.cache.internal.CrossProcessCacheAccess
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.cache.internal.DefaultCacheScopeMapping
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.nativeintegration.filesystem.FileType
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

class DefaultFileContentCacheFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def listenerManager = new DefaultListenerManager()
    def fileSystem = Mock(FileSystem)
    def fileSystemMirror = Mock(FileSystemMirror)
    def hasher = Mock(FileHasher)
    def cacheRepository = new DefaultCacheRepository(new DefaultCacheScopeMapping(tmpDir.file("user-home"), tmpDir.file("build-dir"), GradleVersion.current()), new InMemoryCacheFactory())
    def inMemoryTaskArtifactCache = new InMemoryTaskArtifactCache(false, new CrossBuildInMemoryCacheFactory(new DefaultListenerManager())) {
        @Override
        CacheDecorator decorator(int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
            return new CacheDecorator() {
                @Override
                def <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
                    return persistentCache
                }
            }
        }
    }
    def factory = new DefaultFileContentCacheFactory(listenerManager, hasher, fileSystem, fileSystemMirror, cacheRepository, inMemoryTaskArtifactCache, Stub(Gradle))
    def calculator = Mock(FileContentCacheFactory.Calculator)

    def "calculates entry value for file when not seen before and reuses result"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.file(1234, 4321)
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        1 * calculator.calculate(file, FileType.RegularFile) >> 12
        0 * _

        when:
        result = cache.get(file)

        then:
        result == 12
        0 * _
    }

    def "calculates entry value for directory when not seen before and reuses result"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.directory()
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * calculator.calculate(file, FileType.Directory) >> 12
        0 * _

        when:
        result = cache.get(file)

        then:
        result == 12
        0 * _
    }

    def "reuses calculated value for file across cache instances"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.file(1234, 4321)
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        1 * calculator.calculate(file, FileType.RegularFile) >> 12
        0 * _

        when:
        result = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER).get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        0 * _
    }

    def "reuses result when file content has not changed after task outputs may have changed"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.file(1234, 4321)
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        1 * calculator.calculate(file, FileType.RegularFile) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(TaskOutputsGenerationListener).beforeTaskOutputsGenerated()
        result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        0 * _
    }

    def "calculates result for directory content after task outputs may have changed"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.directory()
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * calculator.calculate(file, FileType.Directory) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(TaskOutputsGenerationListener).beforeTaskOutputsGenerated()
        result = cache.get(file)

        then:
        result == 10
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * calculator.calculate(file, FileType.Directory) >> 10
        0 * _
    }

    def "calculates result when file content has changed"() {
        def file = new File("thing.txt")
        def fileMetadata = DefaultFileMetadata.file(1234, 4321)
        def cache = factory.newCache("cache", 12000, calculator, BaseSerializerFactory.INTEGER_SERIALIZER)

        when:
        def result = cache.get(file)

        then:
        result == 12
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(123)
        1 * calculator.calculate(file, FileType.RegularFile) >> 12
        0 * _

        when:
        listenerManager.getBroadcaster(TaskOutputsGenerationListener).beforeTaskOutputsGenerated()
        result = cache.get(file)

        then:
        result == 10
        1 * fileSystemMirror.getFile(file.absolutePath) >> null
        1 * fileSystem.stat(file) >> fileMetadata
        1 * hasher.hash(file, fileMetadata) >> HashCode.fromInt(321)
        1 * calculator.calculate(file, FileType.RegularFile) >> 10
        0 * _
    }
}
