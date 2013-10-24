/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice
import org.gradle.api.internal.filestore.PathKeyFileStore
import org.gradle.cache.CacheRepository
import org.gradle.cache.DirectoryCacheBuilder
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.FileLockManager
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultCacheLockingManagerTest extends Specification {
    CacheRepository cacheRepository = Mock()
    DirectoryCacheBuilder directoryCacheBuilder = Mock()
    PersistentCache persistentCache = Mock()
    @Rule TestNameTestDirectoryProvider temporaryFolder

    def "Create file store"() {
        given:
        TestFile testCacheDir = temporaryFolder.file("test/cache")

        when:
        CacheLockingManager cacheLockingManager = new DefaultCacheLockingManager(cacheRepository)
        PathKeyFileStore fileStore = cacheLockingManager.createFileStore()

        then:
        1 * cacheRepository.store(CacheLayout.ROOT.getKey()) >> directoryCacheBuilder
        1 * directoryCacheBuilder.withDisplayName("artifact cache") >> directoryCacheBuilder
        1 * directoryCacheBuilder.withLayout(_) >> directoryCacheBuilder
        1 * directoryCacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> directoryCacheBuilder
        1 * directoryCacheBuilder.open() >> persistentCache
        2 * persistentCache.baseDir >> testCacheDir
        fileStore != null
        fileStore.baseDir == new File(testCacheDir, CacheLayout.FILE_STORE.key)
    }

    def "Create metadata store"() {
        given:
        TestFile testCacheDir = temporaryFolder.file("test/cache")

        when:
        CacheLockingManager cacheLockingManager = new DefaultCacheLockingManager(cacheRepository)
        PathKeyFileStore fileStore = cacheLockingManager.createMetaDataStore()

        then:
        1 * cacheRepository.store(CacheLayout.ROOT.getKey()) >> directoryCacheBuilder
        1 * directoryCacheBuilder.withDisplayName("artifact cache") >> directoryCacheBuilder
        1 * directoryCacheBuilder.withLayout(_) >> directoryCacheBuilder
        1 * directoryCacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> directoryCacheBuilder
        1 * directoryCacheBuilder.open() >> persistentCache
        2 * persistentCache.baseDir >> testCacheDir
        fileStore != null
        fileStore.baseDir == new File(testCacheDir, CacheLayout.META_DATA.key + System.properties['file.separator'] + 'descriptors')
    }
}
