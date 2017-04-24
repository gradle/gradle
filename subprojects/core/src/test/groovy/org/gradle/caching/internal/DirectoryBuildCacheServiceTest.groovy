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

package org.gradle.caching.internal

import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.caching.BuildCacheKey
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheServiceTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def cacheDir = temporaryFolder.createDir("cache")
    def persistentCache = Mock(PersistentCache) {
        getBaseDir() >> cacheDir
    }
    def cacheBuilder = Mock(CacheBuilder) {
        open() >> persistentCache
        _ * _(_) >> { cacheBuilder }
    }
    def cacheRepository = Mock(CacheRepository) {
        cache(cacheDir) >> cacheBuilder
    }
    def service = new DirectoryBuildCacheService(cacheRepository, cacheDir)
    def key = Mock(BuildCacheKey)

    def "does not store partial result"() {
        def hashCode = "1234abcd"
        when:
        service.store(key) { OutputStream output ->
            // Check that partial result file is created inside the cache directory
            def cacheDirFiles = cacheDir.listFiles()
            assert cacheDirFiles.length == 1

            def partialCacheFile = cacheDirFiles[0]
            assert partialCacheFile.name.startsWith(hashCode)
            assert partialCacheFile.name.endsWith(".part")

            output << "abcd"
            throw new RuntimeException("Simulated write error")
        }
        then:
        def ex = thrown RuntimeException
        ex.message == "Simulated write error"
        cacheDir.listFiles() as List == []
        1 * key.getHashCode() >> hashCode
    }
}
