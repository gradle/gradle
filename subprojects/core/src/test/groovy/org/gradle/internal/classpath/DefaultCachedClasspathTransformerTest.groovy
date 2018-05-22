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
import org.gradle.internal.Factory
import org.gradle.internal.file.JarCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class DefaultCachedClasspathTransformerTest extends Specification {
    @Rule TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    TestFile testDir = testDirectoryProvider.testDirectory

    TestFile cachedDir = testDir.file("cached")
    TestFile otherStore = testDir.file("other-store").createDir()
    PersistentCache cache = Stub(PersistentCache) {
        getBaseDir() >> cachedDir
        useCache(_) >> { Factory f -> f.create() }
    }
    CacheBuilder cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache(_) >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
        withCleanup(_) >> { cacheBuilder }
    }
    CacheRepository cacheRepository = Stub(CacheRepository) {
        cache(_) >> cacheBuilder
    }
    CachedJarFileStore jarFileStore = Stub(CachedJarFileStore) {
        getFileStoreRoots() >> [otherStore]
    }
    JarCache jarCache = Mock(JarCache)

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(cacheRepository, jarCache, [jarFileStore])

    def "can convert a classpath to cached jars"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File cachedFile = cachedDir.file("file1").createFile()
        File alreadyCachedFile = cachedDir.file("file2").createFile()
        File cachedInOtherStore = otherStore.file("file3").createFile()
        File externalDir = testDir.file("external/dir1").createDir()
        ClassPath classPath = DefaultClassPath.of([externalFile, alreadyCachedFile, cachedInOtherStore, externalDir])

        when:
        ClassPath cachedClassPath = transformer.transform(classPath)

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        cachedClassPath.asFiles == [ cachedFile, alreadyCachedFile, cachedInOtherStore, externalDir ]
    }

    def "can convert a url collection to cached jars"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File cachedFile = cachedDir.file("file1").createFile()
        URL alreadyCachedFile = cachedDir.file("file2").createFile().toURI().toURL()
        URL externalDir = testDir.file("external/dir").createDir().toURI().toURL()
        URL httpURL = new URL("http://some.where.com")

        when:
        Collection<URL> cachedUrls = transformer.transform([externalFile.toURI().toURL(), httpURL, alreadyCachedFile, externalDir])

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        cachedUrls == [ cachedFile.toURI().toURL(), httpURL, alreadyCachedFile, externalDir ]
    }

    def "touches immediate children of cache dir when accessed"() {
        given:
        File externalFile = testDir.file("external/file1").createFile()
        File cachedFile = cachedDir.file("e11f1cf5681161f98a43c55e341f1b93/sub/file1").createFile()
        File alreadyCachedFile = cachedDir.file("file2").createFile()
        File cachedInOtherStore = otherStore.file("file3").createFile()
        [externalFile, cachedFile, cachedFile.parentFile, cachedFile.parentFile.parentFile, alreadyCachedFile, cachedInOtherStore].each { it.lastModified = 0 }
        def beforeAccess = MILLISECONDS.toSeconds(System.currentTimeMillis())

        when:
        transformer.transform(DefaultClassPath.of([externalFile, alreadyCachedFile, cachedInOtherStore]))

        then:
        1 * jarCache.getCachedJar(externalFile, _) >> cachedFile

        and:
        externalFile.lastModified() == 0
        cachedFile.lastModified() == 0
        cachedFile.parentFile.lastModified() == 0
        cachedFile.parentFile.parentFile.lastModified() >= SECONDS.toMillis(beforeAccess)
        alreadyCachedFile.lastModified() >= SECONDS.toMillis(beforeAccess)
        cachedInOtherStore.lastModified() == 0
    }
}
