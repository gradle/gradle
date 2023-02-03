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

package org.gradle.cache.internal

import org.apache.commons.io.FileUtils
import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.internal.GeneratedGradleJarCache.CACHE_DISPLAY_NAME
import static org.gradle.cache.internal.GeneratedGradleJarCache.CACHE_KEY
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode

class DefaultGeneratedGradleJarCacheTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def cacheBuilderFactory = Mock(GlobalScopedCacheBuilderFactory)
    def gradleVersion = GradleVersion.current().version
    def cacheBuilder = Mock(CacheBuilder)
    def cache = Mock(PersistentCache)

    def "can close cache"() {
        when:
        def provider = new DefaultGeneratedGradleJarCache(cacheBuilderFactory, gradleVersion)
        provider.close()

        then:
        1 * cacheBuilderFactory.createCacheBuilder(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.OnDemand)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        1 * cache.close()
    }

    def "creates JAR file on demand for identifier '#identifier'"(String identifier) {
        def cacheDir = tmpDir.testDirectory
        def jarFile = cacheDir.file("gradle-${identifier}-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def provider = new DefaultGeneratedGradleJarCache(cacheBuilderFactory, gradleVersion)
        def resolvedFile = provider.get(identifier) { it.createNewFile() }

        then:
        1 * cacheBuilderFactory.createCacheBuilder(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.OnDemand)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.useCache(_)
        jarFile == resolvedFile

        where:
        identifier << ["api", "test-kit"]
    }

    def "reuses existing JAR file if existent"() {
        def cacheDir = tmpDir.testDirectory
        def jarFile = cacheDir.file("gradle-api-${gradleVersion}.jar")
        def cacheBuilder = Mock(CacheBuilder)
        def cache = Mock(PersistentCache)

        when:
        def provider = new DefaultGeneratedGradleJarCache(cacheBuilderFactory, gradleVersion)
        def resolvedFile = provider.get("api") { it.createNewFile() }

        then:
        1 * cacheBuilderFactory.createCacheBuilder(CACHE_KEY) >> cacheBuilder
        1 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        1 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.OnDemand)) >> cacheBuilder
        1 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.useCache(_)
        jarFile == resolvedFile

        when:
        FileUtils.touch(jarFile)
        resolvedFile = provider.get("api") { Assert.fail("Should not be called if file already exists") }

        then:
        0 * cacheBuilderFactory.createCacheBuilder(CACHE_KEY) >> cacheBuilder
        0 * cacheBuilder.withDisplayName(CACHE_DISPLAY_NAME) >> cacheBuilder
        0 * cacheBuilder.withLockOptions(mode(FileLockManager.LockMode.OnDemand)) >> cacheBuilder
        0 * cacheBuilder.open() >> { cache }
        _ * cache.getBaseDir() >> cacheDir
        1 * cache.useCache(_)
        jarFile == resolvedFile
    }
}
