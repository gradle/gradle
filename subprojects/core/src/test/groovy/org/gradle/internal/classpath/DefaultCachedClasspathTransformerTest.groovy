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

import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.GlobalCacheLocations
import org.gradle.cache.internal.UsedGradleVersions
import org.gradle.cache.scopes.GlobalScopedCache
import org.gradle.internal.Pair
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.hash.Hasher
import org.gradle.internal.io.ClassLoaderObjectInputStream
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.junit.Rule
import spock.lang.Subject

import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic
import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.None

class DefaultCachedClasspathTransformerTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def cachedDir = testDir.file("cached")
    def cache = new TestInMemoryCacheFactory().open(cachedDir, "jars")
    def cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withCrossVersionCache(_) >> { cacheBuilder }
        withLockOptions(_) >> { cacheBuilder }
        withCleanup(_) >> { cacheBuilder }
    }
    def cacheRepository = Stub(GlobalScopedCache) {
        crossVersionCache(_) >> cacheBuilder
    }
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def usedGradleVersions = Stub(UsedGradleVersions)

    def cacheFactory = new DefaultClasspathTransformerCacheFactory(usedGradleVersions)
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def classpathBuilder = new ClasspathBuilder(TestFiles.tmpDirTemporaryFileProvider(testDirectoryProvider.root))
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def globalCacheLocations = Stub(GlobalCacheLocations)
    def fileLockManager = Stub(FileLockManager)
    URLClassLoader testClassLoader = null

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(
        cacheRepository,
        cacheFactory,
        fileAccessTimeJournal,
        classpathWalker,
        classpathBuilder,
        fileSystemAccess,
        executorFactory,
        globalCacheLocations,
        fileLockManager
    )

    def cleanup() {
        testClassLoader?.close()
    }

    def "does nothing to empty classpath when transform is none"() {
        given:
        def classpath = DefaultClassPath.of()

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "does nothing to empty classpath when transform is build logic"() {
        given:
        def classpath = DefaultClassPath.of()

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "discards missing file when transform is none"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "discards missing file when transform is build logic"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file into cache when transform is none"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")

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
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")
        transformer.transform(classpath, None)

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "reuses file from its origin cache when transform is none"() {
        given:
        def file = testDir.file("other/thing.jar")
        _ * globalCacheLocations.isInsideGlobalCache(file.absolutePath) >> true
        jar(file)
        def classpath = DefaultClassPath.of(file)
        transformer.transform(classpath, None)

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [file]

        and:
        0 * fileAccessTimeJournal._
    }

    def "copies file into cache when content has changed and transform is none"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")
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

    def "transforms file into cache when transform is build logic"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/76ae7e65d5b1f0eec6e7d41da2c8fa50/thing.jar")

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

    def "transforms directory into cache when usage is build logic"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)
        def cachedFile = testDir.file("cached/0518a2462549d870edeb36a1f69858e6/thing.dir.jar")

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

    def "transforms multiple entries into cache when usage is build logic"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(dir, file)
        def cachedDir = testDir.file("cached/0518a2462549d870edeb36a1f69858e6/thing.dir.jar")
        def cachedFile = testDir.file("cached/76ae7e65d5b1f0eec6e7d41da2c8fa50/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedDir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedDir.parentFile, _)
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "removes entries with duplicate content when usage is none"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def file = testDir.file("thing.jar")
        jar(file)
        def dir2 = testDir.file("thing2.dir")
        classesDir(dir2)
        def file2 = testDir.file("thing2.jar")
        jar(file2)
        def dir3 = testDir.file("thing3.dir")
        classesDir(dir3)
        def file3 = testDir.file("thing3.jar")
        jar(file3)
        def classpath = DefaultClassPath.of(dir, file, dir2, file2, dir3, file3)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, None)

        then:
        cachedClasspath.asFiles == [dir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "removes entries with duplicate content when usage is build logic"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def file = testDir.file("thing.jar")
        jar(file)
        def dir2 = testDir.file("thing2.dir")
        classesDir(dir2)
        def file2 = testDir.file("thing2.jar")
        jar(file2)
        def dir3 = testDir.file("thing3.dir")
        classesDir(dir3)
        def file3 = testDir.file("thing3.jar")
        jar(file3)
        def classpath = DefaultClassPath.of(dir, file, dir2, file2, dir3, file3)
        def cachedDir = testDir.file("cached/0518a2462549d870edeb36a1f69858e6/thing.dir.jar")
        def cachedFile = testDir.file("cached/76ae7e65d5b1f0eec6e7d41da2c8fa50/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, BuildLogic)

        then:
        cachedClasspath.asFiles == [cachedDir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedDir.parentFile, _)
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "applies client provided transform to file"() {
        given:
        def transform = Mock(CachedClasspathTransformer.Transform)
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/c5b35b608c1859c98fc90f6b87b8042b/thing.jar")

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

    def "uses non-file URL from origin"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def remote = new URL("https://somewhere")
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")

        when:
        def cachedClasspath = transformer.transform([file.toURI().toURL(), remote], None)

        then:
        cachedClasspath == [cachedFile.toURI().toURL(), remote]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "transforms class to intercept calls to System.getProperty()"() {
        given:
        def listener = Mock(Instrumented.Listener)
        Instrumented.setListener(listener)
        def cl = transformAndLoad(SystemPropertyAccessingThing)

        when:
        cl.readProperty()

        then:
        1 * listener.systemPropertyQueried("prop", null, SystemPropertyAccessingThing.name)
        0 * listener._

        cleanup:
        Instrumented.discardListener()
    }

    def "transforms Java lambda Action implementations so they can be serialized"() {
        given:
        def cl = transformAndLoad(ClassWithActionLambda)

        expect:
        def original = cl.action(123)
        original instanceof Serializable

        def action = recreate(original)
        def result = new StringBuilder()
        action.execute(result)

        result.toString() == "123"
    }

    def "class can include both serializable lambda and Action implementations"() {
        given:
        def cl = transformAndLoad(ClassWithActionAndSerializableLambda, ClassWithActionAndSerializableLambda.SerializableThing)

        expect:
        def original1 = cl.action(123)
        original1 instanceof Serializable

        def recreated1 = recreate(original1)
        def result1 = new StringBuilder()
        recreated1.execute(result1)

        result1.toString() == "123"

        def original2 = cl.thing(123)
        def result2 = recreate(original2).call()

        result2 == "123"
    }

    def "class can include only serializable lambda"() {
        given:
        def cl = transformAndLoad(ClassWithSerializableLambda, ClassWithSerializableLambda.SerializableThing)

        expect:
        def original = cl.thing(123)
        def result = recreate(original).call()

        result == "123"
    }

    def "interface can include only serializable lambda"() {
        given:
        def cl = transformAndLoad(SerializableLambda)

        expect:
        def original = cl.thing(123)
        def result = recreate(original).call()

        result == "123"
    }

    Object recreate(Object value) {
        def outputStream = new ByteArrayOutputStream()
        new ObjectOutputStream(outputStream).with {
            writeObject(value)
            flush()
        }
        return new ClassLoaderObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()), value.class.classLoader).readObject()
    }

    Class transformAndLoad(Class cl, Class... additional) {
        def jar = testDir.file("${cl.name}.jar")
        classpathBuilder.jar(jar) { builder ->
            ([cl] + additional.toList()).forEach { required ->
                def fileName = required.name.replace('.', '/') + ".class"
                def content = required.classLoader.getResource(fileName).bytes
                builder.put(fileName, content)
            }
        }
        def transformed = transformer.transform(DefaultClassPath.of(jar), BuildLogic)
        def filtering = new FilteringClassLoader(getClass().classLoader, new FilteringClassLoader.Spec([Action.name, Instrumented.name], [], [], [], [], [], []))
        testClassLoader = new URLClassLoader(transformed.asURLArray, filtering)
        return testClassLoader.loadClass(cl.name)
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
