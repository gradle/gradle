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

package org.gradle.caching.local.internal

import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CleanupAction
import org.gradle.cache.UnscopedCacheBuilderFactory
import org.gradle.cache.internal.CleanupActionDecorator
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.resource.local.PathKeyFileStore
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class DirectoryBuildCacheServiceFactoryTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def cacheRepository = Mock(UnscopedCacheBuilderFactory)
    def globalScopedCache = Mock(GlobalScopedCacheBuilderFactory)
    def resolver = Mock(FileResolver)
    def fileStoreFactory = Mock(DirectoryBuildCacheFileStoreFactory)
    def cleanupActionDecorator = Mock(CleanupActionDecorator)
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def factory = new DirectoryBuildCacheServiceFactory(cacheRepository, globalScopedCache, resolver, fileStoreFactory, cleanupActionDecorator, fileAccessTimeJournal, TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.root))
    def cacheBuilder = Stub(CacheBuilder)
    def config = Mock(DirectoryBuildCache)
    def buildCacheDescriber = new NoopBuildCacheDescriber()

    def "can create service with default directory"() {
        def cacheDir = temporaryFolder.file("build-cache-1")

        when:
        def service = factory.createBuildCacheService(config, buildCacheDescriber)
        then:
        service instanceof DirectoryBuildCacheService
        1 * config.getDirectory() >> null
        1 * config.getRemoveUnusedEntriesAfterDays() >> 10
        1 * globalScopedCache.baseDirForCrossVersionCache("build-cache-1") >> cacheDir
        1 * fileStoreFactory.createFileStore(cacheDir) >> Mock(PathKeyFileStore)
        1 * cacheRepository.cache(cacheDir) >> cacheBuilder
        1 * cleanupActionDecorator.decorate(_) >> Mock(CleanupAction)
        0 * _
    }

    def "can create service with given directory"() {
        def cacheDir = temporaryFolder.file("cache-dir")

        when:
        def service = factory.createBuildCacheService(config, buildCacheDescriber)
        then:
        service instanceof DirectoryBuildCacheService
        1 * config.getDirectory() >> cacheDir
        1 * config.getRemoveUnusedEntriesAfterDays() >> 10
        1 * resolver.resolve(cacheDir) >> cacheDir
        1 * fileStoreFactory.createFileStore(cacheDir) >> Mock(PathKeyFileStore)
        1 * cacheRepository.cache(cacheDir) >> cacheBuilder
        1 * cleanupActionDecorator.decorate(_) >> Mock(CleanupAction)
        0 * _
    }

    private class NoopBuildCacheDescriber implements BuildCacheServiceFactory.Describer {

        @Override
        BuildCacheServiceFactory.Describer type(String type) { this }

        @Override
        BuildCacheServiceFactory.Describer config(String name, String value) { this }

    }
}
