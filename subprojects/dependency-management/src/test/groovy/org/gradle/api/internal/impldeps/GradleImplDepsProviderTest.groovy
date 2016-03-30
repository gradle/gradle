/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.impldeps

import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.FileLockManager
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.impldeps.GradleImplDepsProvider.CACHE_DISPLAY_NAME
import static org.gradle.api.internal.impldeps.GradleImplDepsProvider.CACHE_KEY
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class GradleImplDepsProviderTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def cacheRepository = Mock(CacheRepository)
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def gradleVersion = GradleVersion.current().version
    def cacheBuilder = Mock(CacheBuilder)
    def cache = Mock(PersistentCache)

    def "can close cache"() {
        when:
        def provider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, gradleVersion)
        provider.close()

        then:
        1 * cacheRepository.cache(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        1 * cache.close()
    }

    def "returns null for unknown JAR file name"() {
        when:
        def provider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, gradleVersion)
        provider.getFile(Collections.emptyList(), 'unknown') == null

        then:
        1 * cacheRepository.cache(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        0 * cache._
    }

    @Unroll
    def "creates JAR file on demand for name '#name'"() {
        def cacheDir = tmpDir.testDirectory
        def jar = tmpDir.createDir('originalJars').file('mydep-1.2.jar')
        def jarFile = cacheDir.file("gradle-${name}-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def provider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, gradleVersion)
        def resolvedFile = provider.getFile([jar], name)

        then:
        1 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.useCache("Generating $jarFile.name", _)
        jarFile == resolvedFile

        where:
        name << GradleImplDepsProvider.GradleImplDepsJar.allIdentifiers
    }

    def "reuses existing JAR file if existent"() {
        def cacheDir = tmpDir.testDirectory
        def jar = tmpDir.createDir('originalJars').file('mydep-1.2.jar')
        def jarFile = cacheDir.file("gradle-api-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def provider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, gradleVersion)
        def resolvedFile = provider.getFile([jar], 'api')

        then:
        1 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.useCache("Generating $jarFile.name", _)
        jarFile == resolvedFile

        when:
        GFileUtils.touch(jarFile)
        resolvedFile = provider.getFile([jar], 'api')

        then:
        0 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        0 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        0 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.None)) >> cacheBuilder
        0 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        0 * cache.useCache("Generating $jarFile.name", _)
        jarFile == resolvedFile
    }
}
