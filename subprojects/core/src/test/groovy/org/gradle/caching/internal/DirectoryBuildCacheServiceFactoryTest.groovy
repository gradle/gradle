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

import org.gradle.api.internal.file.FileResolver
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.VersionStrategy
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheServiceFactoryTest extends Specification {
    def cacheRepository = Mock(CacheRepository)
    def cacheScopeMapping = Mock(CacheScopeMapping)
    def resolver = Mock(FileResolver)
    def factory = new DirectoryBuildCacheServiceFactory(cacheRepository, cacheScopeMapping, resolver)
    def cacheBuilder = Stub(CacheBuilder)
    def config = Mock(DirectoryBuildCache)

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def "can create service with default directory"() {
        def cacheDir = temporaryFolder.file("build-cache-1")

        when:
        def service = factory.createBuildCacheService(config)
        then:
        service instanceof DirectoryBuildCacheService
        1 * config.getDirectory() >> null
        1 * cacheScopeMapping.getBaseDirectory(null, "build-cache-1", VersionStrategy.SharedCache) >> cacheDir
        1 * cacheRepository.cache(cacheDir) >> cacheBuilder
        0 * _
    }

    def "can create service with given directory"() {
        def cacheDir = temporaryFolder.file("cache-dir")

        when:
        def service = factory.createBuildCacheService(config)
        then:
        service instanceof DirectoryBuildCacheService
        1 * config.getDirectory() >> cacheDir
        1 * resolver.resolve(cacheDir) >> cacheDir
        1 * cacheRepository.cache(cacheDir) >> cacheBuilder
        0 * _
    }
}
