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
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class GradleImplDepsProviderTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def cacheRepository = Mock(CacheRepository)
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def gradleVersion = GradleVersion.current().version
    def relocatedJarCreator = Mock(RelocatedJarCreator)
    def provider = new GradleImplDepsProvider(cacheRepository, progressLoggerFactory, gradleVersion)

    def setup() {
        provider.relocatedJarCreator = relocatedJarCreator
    }

    def "returns null for unknown JAR file name"() {
        expect:
        provider.getFile(Collections.emptyList(), 'unknown') == null
    }

    @Unroll
    def "creates JAR file on demand for name '#name'"() {
        def cacheDir = tmpDir.testDirectory
        def jar = tmpDir.createDir('originalJars').file('mydep-1.2.jar')
        def classpath = [jar]
        def jarFile = cacheDir.file("gradle-${name}-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def resolvedFile = provider.getFile([jar], name)

        then:
        1 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        0 * cache._
        1 * relocatedJarCreator.create(jarFile, classpath)
        jarFile == resolvedFile

        where:
        name << GradleImplDepsProvider.VALID_JAR_NAMES
    }

    def "reuses existing JAR file if existent"() {
        def cacheDir = tmpDir.testDirectory
        def jar = tmpDir.createDir('originalJars').file('mydep-1.2.jar')
        def classpath = [jar]
        def jarFile = cacheDir.file("gradle-api-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def resolvedFile = provider.getFile([jar], 'api')

        then:
        1 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        0 * cache._
        1 * relocatedJarCreator.create(jarFile, classpath)
        jarFile == resolvedFile

        when:
        GFileUtils.touch(jarFile)
        resolvedFile = provider.getFile([jar], 'api')

        then:
        0 * cacheRepository.cache(GradleImplDepsProvider.CACHE_KEY) >> cacheBuilder
        0 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        0 * cache._
        0 * relocatedJarCreator.create(jarFile, classpath)
        jarFile == resolvedFile
    }
}
