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
import org.gradle.internal.Pair
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.Hasher
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic
import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.None

class DefaultCachedClasspathTransformerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def cachedDir = testDir.file("cached")
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

    def "skips missing file when transform is none"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "skips missing file when tranform is build logic"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when transform is none"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_d3714f1fd48ab27e701a9c39545ae221/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "reuses file from cache when transform is none"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_d3714f1fd48ab27e701a9c39545ae221/thing.jar")
        transformer.transform(classpath, None)

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when content has changed and transform is none"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_d3714f1fd48ab27e701a9c39545ae221/thing.jar")
        transformer.transform(classpath, None)
        modifiedJar(file)

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "reuses directory from its original location when transform is none"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [dir]

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file to cache when transform is build logic"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/349f8baba38535e2ce3916f4d42f83ee/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._

        when:
        def cachedClasspath2 = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copies directory to cache when usage is build logic"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)
        def cachedFile = testDir.file("cached/33fbc773e43e04aa1837a9e4e20be853/thing.dir.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._

        when:
        def cachedClasspath2 = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "applies transform to file"() {
        given:
        def transform = Mock(CachedClasspathTransformer.Transform)
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/1a0547a447cfc594a350aa4dbb30ae3d/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic, transform)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * transform.applyConfigurationTo(_) >> { Hasher hasher -> hasher.putInt(123) }
        1 * transform.apply(_, _) >> { entry, visitor ->
            assert entry.name == "a.class"
            Pair.of(entry.path, visitor)
        }
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * _

        when:
        def cachedClasspath2 = transformer.transform(classpath, BuildLogic, transform)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * transform.applyConfigurationTo(_) >> { Hasher hasher -> hasher.putInt(123) }
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * _
    }

    @Ignore
    def "reuses non-file URL from origin"() {
        expect: false
    }

    void classesDir(TestFile dir) {
        dir.deleteDir()
        dir.createDir()
        dir.file("a.class").bytes = classOne()
    }

    void jar(TestFile file) {
        classpathBuilder.jar(file) {
            it.put("a.class", classOne())
        }
    }

    void modifiedJar(TestFile file) {
        classpathBuilder.jar(file) {
            it.put("b.class", classTwo())
        }
    }

    byte[] classOne() {
        return getClass().classLoader.getResource(SystemPropertyAccessingThing.name.replace('.', '/') + ".class").bytes
    }

    byte[] classTwo() {
        return getClass().classLoader.getResource(AnotherSystemPropertyAccessingThing.name.replace('.', '/') + ".class").bytes
    }
}
