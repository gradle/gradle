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

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.UsedGradleVersions
import org.gradle.internal.Factory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.classpath.CachedClasspathTransformer.Usage.BuildLogic
import static org.gradle.internal.classpath.CachedClasspathTransformer.Usage.Other

class DefaultCachedClasspathTransformerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def cachedDir = testDir.file("cached")
    def otherStore = testDir.file("other-store").createDir()
    def cache = Stub(PersistentCache) {
        getBaseDir() >> cachedDir
        useCache(_) >> { Factory f -> f.create() }
    }
    def cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache(_) >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
        withCleanup(_) >> { cacheBuilder }
    }
    def cacheScopeMapping = Stub(CacheScopeMapping) {
        getBaseDirectory(_, _, _) >> cachedDir
    }
    def cacheRepository = Stub(CacheRepository) {
        cache(_) >> cacheBuilder
    }
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def usedGradleVersions = Stub(UsedGradleVersions)

    def cacheFactory = new DefaultClasspathTransformerCacheFactory(cacheScopeMapping, usedGradleVersions)
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def classpathBuilder = new ClasspathBuilder()
    def virtualFileSystem = TestFiles.virtualFileSystem()

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(cacheRepository, cacheFactory, fileAccessTimeJournal, classpathWalker, classpathBuilder, virtualFileSystem)

    def "skips missing file when usage is unknown"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, Other)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "skips missing file when usage is for build logic"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when usage is unknown"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_0e41e71646135089ef78f7bf6c14219f/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, Other)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "reuses file from cache when usage is unknown"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_0e41e71646135089ef78f7bf6c14219f/thing.jar")
        transformer.transform(classpath, Other)

        when:
        def cachedClasspath = transformer.transform(classpath, Other)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when content has changed and usage is unknown"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_0e41e71646135089ef78f7bf6c14219f/thing.jar")
        transformer.transform(classpath, Other)
        modifiedJar(file)

        when:
        def cachedClasspath = transformer.transform(classpath, Other)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "reuses directory from its original location when usage is unknown"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)

        when:
        def cachedClasspath = transformer.transform(classpath, Other)

        then:
        cachedClasspath.asFiles == [dir]

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when usage is build logic"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/0e41e71646135089ef78f7bf6c14219f/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copies directory to cache when usage is build logic"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)
        def cachedFile = testDir.file("cached/7fa7533a5f167b200591bd37a63fb084/thing.dir.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    @Ignore
    def "reuses non-file URL from origin"() {
        expect: false
    }

    void classesDir(TestFile dir) {
        dir.deleteDir()
        dir.createDir()
        dir.file("a.class").bytes = "class".bytes
    }

    void jar(TestFile file) {
        classpathBuilder.jar(file) {
            it.put("a.class", "class".bytes)
        }
    }

    void modifiedJar(TestFile file) {
        classpathBuilder.jar(file) {
            it.put("b.class", "class".bytes)
        }
    }
}
