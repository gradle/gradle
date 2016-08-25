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

package org.gradle.internal.classpath

import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.internal.file.JarCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject


class DefaultCachedClasspathTransformerTest extends Specification {
    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    TestFile testDir = testDirectoryProvider.testDirectory

    PersistentCache cache = Stub(PersistentCache) {
        useCache(_, _) >> { args -> args[1].create() }
    }
    CacheBuilder cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache() >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
    }
    CacheRepository cacheRepository = Stub(CacheRepository) {
        cache(_) >> cacheBuilder
    }
    JarCache jarCache = Mock(JarCache)
    File dummyCacheDir = Stub(File) {
        getParent() >> testDir.file("cached").path
    }
    CacheScopeMapping cacheScopeMapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> dummyCacheDir
    }

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(cacheRepository, jarCache, cacheScopeMapping)

    def "can convert a classpath to cached jars"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File cachedFile = testDir.file("cached/file1").createFile()
        File alreadyCachedFile = testDir.file("cached/file2").createFile()
        ClassPath classPath = DefaultClassPath.of([externalFile, alreadyCachedFile])

        when:
        ClassPath cachedClassPath = transformer.transform(classPath)

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        cachedClassPath.asFiles == [ cachedFile, alreadyCachedFile ]
    }

    def "can convert a url collection to cached jars"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File cachedFile = testDir.file("cached/file1").createFile()
        URL httpURL = new URL("http://some.where.com")

        when:
        Collection<URL> cachedUrls = transformer.transform([externalFile.toURI().toURL(), httpURL])

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        cachedUrls == [ cachedFile.toURI().toURL(), httpURL ]
    }
}
