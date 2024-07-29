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

import org.gradle.api.file.RelativePath
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.GlobalCacheLocations
import org.gradle.cache.internal.UsedGradleVersions
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.Pair
import org.gradle.internal.classpath.transforms.ClassTransform
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter
import org.gradle.internal.hash.Hasher
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryCacheFactory
import org.junit.Rule
import org.objectweb.asm.ClassVisitor
import spock.lang.Subject

import java.util.zip.ZipEntry

class DefaultCachedClasspathTransformerTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory

    def cachedDir = testDir.file("cached")
    def cache = new TestInMemoryCacheFactory().open(cachedDir, "jars")
    def cacheBuilder = Stub(CacheBuilder) {
        open() >> cache
        withDisplayName(_) >> { cacheBuilder }
        withInitialLockMode(_) >> { cacheBuilder }
        withCleanupStrategy(_) >> { cacheBuilder }
    }
    def cacheBuilderFactory = Stub(GlobalScopedCacheBuilderFactory) {
        createCrossVersionCacheBuilder(_) >> cacheBuilder
    }
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    def usedGradleVersions = Stub(UsedGradleVersions)
    def cacheConfigurations = Stub(CacheConfigurationsInternal)
    def cacheFactory = new DefaultClasspathTransformerCacheFactory(usedGradleVersions, cacheConfigurations)
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def classpathBuilder = new DefaultClasspathBuilder(TestFiles.tmpDirTemporaryFileProvider(testDirectoryProvider.createDir("tmp")))
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def globalCacheLocations = Stub(GlobalCacheLocations)
    def fileLockManager = Stub(FileLockManager)
    def classpathFingerprinter = Stub(ClasspathFingerprinter) {
        fingerprint(_, _) >> { FileSystemSnapshot snapshot, FileCollectionFingerprint previous ->
            Stub(CurrentFileCollectionFingerprint) {
                getHash() >> (snapshot as FileSystemLocationSnapshot).hash
            }
        }
    }
    def classpathElementTransformFactoryForLegacy = new ClasspathElementTransformFactoryForLegacy(classpathBuilder, classpathWalker)
    def noOpCustomTransform = new ClassTransform() {
        @Override
        void applyConfigurationTo(Hasher hasher) {
        }

        @Override
        Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) throws IOException {
            return Pair.of(entry.path, visitor)
        }
    }

    URLClassLoader testClassLoader = null

    @Subject
    DefaultCachedClasspathTransformer transformer = new DefaultCachedClasspathTransformer(
        cacheBuilderFactory,
        cacheFactory,
        fileAccessTimeJournal,
        classpathFingerprinter,
        fileSystemAccess,
        executorFactory,
        globalCacheLocations,
        fileLockManager,
        classpathElementTransformFactoryForLegacy
    )

    def cleanup() {
        testClassLoader?.close()
    }

    def "copying transform does nothing to empty classpath"() {
        given:
        def classpath = DefaultClassPath.of()

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "custom transform does nothing to empty classpath"() {
        given:
        def classpath = DefaultClassPath.of()

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "copying transform discards missing file"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "custom transform discards missing file"() {
        given:
        def classpath = DefaultClassPath.of(testDir.file("missing"))

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.empty

        and:
        0 * fileAccessTimeJournal._
    }

    def "copying transform copies file into cache"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copying transform reuses file from cache"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")
        transformer.copyingTransform(classpath)

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copying transform reuses file from its origin cache"() {
        given:
        def file = testDir.file("other/thing.jar")
        _ * globalCacheLocations.isInsideGlobalCache(file.absolutePath) >> true
        jar(file)
        def classpath = DefaultClassPath.of(file)
        transformer.copyingTransform(classpath)

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [file]

        and:
        0 * fileAccessTimeJournal._
    }

    def "copying transform copies file into cache when content has changed"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")
        transformer.copyingTransform(classpath)
        modifiedJar(file)

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copying transform reuses directory from its original location"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)

        when:
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [dir]

        and:
        0 * fileAccessTimeJournal._
    }

    def "custom transform transforms file into cache"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/886ef9a57c5a3916bcd98f1162c2b925/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.asFiles == [cachedFile]
        cachedFile.assertIsFile()

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._

        when:
        def cachedClasspath2 = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "custom transform transforms directory into cache"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def classpath = DefaultClassPath.of(dir)
        def cachedFile = testDir.file("cached/57c791ec01b383c61cd1941d3babdcbc/thing.dir")

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.asFiles == [cachedFile]
        cachedFile.assertIsDir()

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._

        when:
        def cachedClasspath2 = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "custom transform transforms multiple entries into cache"() {
        given:
        def dir = testDir.file("thing.dir")
        classesDir(dir)
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(dir, file)
        def cachedDir = testDir.file("cached/57c791ec01b383c61cd1941d3babdcbc/thing.dir")
        def cachedFile = testDir.file("cached/886ef9a57c5a3916bcd98f1162c2b925/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.asFiles == [cachedDir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedDir.parentFile, _)
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "copying transform removes entries with duplicate content"() {
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
        def cachedClasspath = transformer.copyingTransform(classpath)

        then:
        cachedClasspath.asFiles == [dir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "custom transform removes entries with duplicate content"() {
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
        def cachedDir = testDir.file("cached/57c791ec01b383c61cd1941d3babdcbc/thing.dir")
        def cachedFile = testDir.file("cached/886ef9a57c5a3916bcd98f1162c2b925/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.asFiles == [cachedDir, cachedFile]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedDir.parentFile, _)
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
    }

    def "applies client provided transform to file"() {
        given:
        def transform = Mock(ClassTransform)
        def file = testDir.file("thing.jar")
        jar(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/7e24674c3afc724bf3a9a45f567ec286/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, transform)

        then:
        cachedClasspath.asFiles == [cachedFile]

        and:
        1 * transform.applyConfigurationTo(_) >> { Hasher hasher -> hasher.putInt(123) }
        1 * transform.apply(_, _, _) >> { entry, visitor, data ->
            assert entry.name == "a.class"
            Pair.of(entry.path, visitor)
        }
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * _

        when:
        def cachedClasspath2 = transformer.transform(classpath, transform)

        then:
        cachedClasspath2.asFiles == [cachedFile]

        and:
        1 * transform.applyConfigurationTo(_) >> { Hasher hasher -> hasher.putInt(123) }
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * _
    }

    def "transformation keeps the compression level of archive entries"() {
        given:
        def file = testDir.file("thing.jar")
        jarWithStoredResource(file)
        def classpath = DefaultClassPath.of(file)
        def cachedFile = testDir.file("cached/c74031ab9e94d160bc953e94f7adb0d3/thing.jar")

        when:
        def cachedClasspath = transformer.transform(classpath, noOpCustomTransform)

        then:
        cachedClasspath.asFiles == [cachedFile]
        def zip = new ZipTestFixture(cachedFile)
        zip.hasCompression("a.class", ZipEntry.DEFLATED)
        zip.hasCompression("res.txt", ZipEntry.STORED)
    }

    def "uses non-file URL from origin"() {
        given:
        def file = testDir.file("thing.jar")
        jar(file)
        def remote = new URL("https://somewhere")
        def cachedFile = testDir.file("cached/o_e161f24809571a55f09d3f820c8e5942/thing.jar")

        when:
        def cachedClasspath = transformer.copyingTransform([file.toURI().toURL(), remote])

        then:
        cachedClasspath == [cachedFile.toURI().toURL(), remote]

        and:
        1 * fileAccessTimeJournal.setLastAccessTime(cachedFile.parentFile, _)
        0 * fileAccessTimeJournal._
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

    void jarWithStoredResource(TestFile file) {
        classpathBuilder.jar(file) {
            it.put("a.class", classOne(), ClasspathEntryVisitor.Entry.CompressionMethod.DEFLATED)
            it.put("res.txt", "resource".bytes, ClasspathEntryVisitor.Entry.CompressionMethod.STORED)
        }
    }

    byte[] classOne() {
        return getClass().classLoader.getResource(SystemPropertyAccessingThing.name.replace('.', '/') + ".class").bytes
    }

    byte[] classTwo() {
        return getClass().classLoader.getResource(AnotherSystemPropertyAccessingThing.name.replace('.', '/') + ".class").bytes
    }
}
