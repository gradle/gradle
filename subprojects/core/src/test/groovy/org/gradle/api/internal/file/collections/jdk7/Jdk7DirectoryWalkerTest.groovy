/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.file.collections.jdk7

import com.google.common.base.Charsets
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.mirror.MirrorUpdatingDirectoryWalker
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DefaultDirectoryWalker
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.DirectoryWalker
import org.gradle.api.internal.file.collections.ReproducibleDirectoryWalker
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.internal.MutableBoolean
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.SetSystemProperties
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory

@UsesNativeServices
class Jdk7DirectoryWalkerTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @Rule
    SetSystemProperties setSystemPropertiesRule


    def cleanup() {
        Charset.defaultCharset = null // clear cache
    }

    // java.nio2 cannot access files with unicode characters when using single-byte non-unicode platform encoding
    // bug seems to show up only on JDK7 when file.encoding != sun.jnu.encoding
    @Issue("GRADLE-2181")
    @Requires(adhoc = { JavaVersion.current().isJava7() && (System.getProperty("sun.jnu.encoding") == null || Charset.forName(System.getProperty("sun.jnu.encoding")).contains(Charsets.UTF_8)) })
    def "check that JDK7 walker gets picked with Unicode encoding as default"() {
        setup:
        System.setProperty("file.encoding", fileEncoding)
        Charset.defaultCharset = null
        def directoryWalkerFactory = directoryFileTreeFactory().create(tmpDir.createDir("root")).directoryWalkerFactory
        directoryWalkerFactory.reset()
        expect:
        directoryWalkerFactory.create().class.simpleName == expectedClassName
        where:
        fileEncoding | expectedClassName
        "UTF-8"      | "Jdk7DirectoryWalker"
        "UTF-16be" | "Jdk7DirectoryWalker"
        "UTF-16le" | "Jdk7DirectoryWalker"
        "UTF-16"   | "Jdk7DirectoryWalker"
        "ISO-8859-1" | "DefaultDirectoryWalker"
    }

    @Unroll
    def "basic directory walking works - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        when:
        def visited = walkDirForPaths(walkerInstance, rootDir, patterns)

        then:
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        !visited.contains(notTextFile.absolutePath)
        !visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker(), directorySnapshotter()]
    }

    def "both DirectoryWalker implementations return same set of files and attributes"() {
        given:
        def rootDir = tmpDir.createDir("root")
        generateFilesAndSubDirectories(rootDir, 10, 5, 3, 1, new AtomicInteger(0))

        when:
        def visitedWithJdk7Walker = walkFiles(rootDir, new Jdk7DirectoryWalker())
        def visitedWithDefaultWalker = walkFiles(rootDir, new DefaultDirectoryWalker())

        then:
        visitedWithDefaultWalker.size() == 340
        visitedWithDefaultWalker.size() == visitedWithJdk7Walker.size()
        checkFileVisitDetailsEqual(visitedWithDefaultWalker, visitedWithJdk7Walker)
    }

    private static void checkFileVisitDetailsEqual(List<FileVisitDetails> visitedWithDefaultWalker, List<FileVisitDetails> visitedWithJdk7Walker) {
        visitedWithDefaultWalker.each { FileVisitDetails details ->
            def detailsFromJdk7Walker = visitedWithJdk7Walker.find { it.file.absolutePath == details.file.absolutePath }

            assert detailsFromJdk7Walker != null &&
                millisToSeconds(details.lastModified) == millisToSeconds(detailsFromJdk7Walker.lastModified) &&
                details.directory == detailsFromJdk7Walker.directory &&
                (details.directory || details.size == detailsFromJdk7Walker.size)
        }
    }

    private static long millisToSeconds(long millis) {
        millis / 1000L
    }

    private static generateFilesAndSubDirectories(TestFile parentDir, int fileCount, int dirCount, int maxDepth, int currentDepth, AtomicInteger fileIdGenerator) {
        for (int i = 0; i < fileCount; i++) {
            parentDir.createFile("file" + fileIdGenerator.incrementAndGet()) << ("x" * fileIdGenerator.get())
        }
        if (currentDepth < maxDepth) {
            for (int i = 0; i < dirCount; i++) {
                TestFile subDir = parentDir.createDir("dir" + fileIdGenerator.incrementAndGet())
                generateFilesAndSubDirectories(subDir, fileCount, dirCount, maxDepth, currentDepth + 1, fileIdGenerator)
            }
        }
    }

    private static List<FileVisitDetails> walkFiles(rootDir, walkerInstance) {
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        def visited = []
        def visitClosure = { visited << it }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor
        fileTree.visit(fileVisitor)
        visited
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "symbolic links for directories are handled properly - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("a/b")
        def file = dir.file("c.txt").createFile()
        file << "Hello world"
        def link = rootDir.file("a/d")
        link.createLink(dir)

        when:
        def visited = walkDirForPaths(walkerInstance, rootDir, new PatternSet())

        then:
        visited.contains(file.absolutePath)
        visited.contains(link.file("c.txt").absolutePath)
        link.file("c.txt").text == "Hello world"

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker(), directorySnapshotter()]
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "symbolic links for files are handled properly - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("a/b")
        def file = dir.file("c.txt").createFile()
        file << "Hello world"
        def link = rootDir.file("a/d").createDir().file("e.txt")
        link.createLink(file)

        when:
        def visited = walkDirForPaths(walkerInstance, rootDir, new PatternSet())

        then:
        visited.contains(file.canonicalPath)
        visited.contains(link.canonicalPath)
        link.text == "Hello world"

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker(), directorySnapshotter()]
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "missing symbolic link causes an exception - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("a/b")
        def link = rootDir.file("a/d")
        link.createLink(dir)

        when:
        dir.deleteDir()
        walkDirForPaths(walkerInstance, rootDir, new PatternSet())

        then:
        GradleException e = thrown()
        e.message.contains("Could not list contents of '${link.absolutePath}'.")

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker(), directorySnapshotter()]
    }

    @Issue("GRADLE-3400")
    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "missing symbolic link that gets filtered doesn't cause an exception - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("target")
        def link = rootDir.file("source")
        link.createLink(dir)
        dir.deleteDir()
        def file = rootDir.createFile("hello.txt")
        file << "Hello world"

        def patternSet = new PatternSet()
        patternSet.include("*.txt")

        when:
        def visited = walkDirForPaths(walkerInstance, rootDir, patternSet)

        then:
        visited.size() == 1
        visited[0] == file.absolutePath

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker(), directorySnapshotter()]
    }

    def "file walker sees a snapshot of file metadata even if files are deleted after walking has started"() {
        given:
        def rootDir = tmpDir.createDir("root")
        long minimumTimestamp = (System.currentTimeMillis()/1000 * 1000) - 2000
        def file1 = rootDir.createFile("a/b/1.txt")
        file1 << '12345'
        def file2 = rootDir.createFile("a/b/2.txt")
        file2 << '12345'
        def file3 = rootDir.createFile("a/b/3.txt")
        file3 << '12345'
        def walkerInstance = new Jdk7DirectoryWalker()
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        def visitedFiles = []
        def visitedDirectories = []
        def fileVisitor = [visitFile: { visitedFiles << it }, visitDir: { visitedDirectories << it }] as FileVisitor

        when:
        fileTree.visit(fileVisitor)
        rootDir.deleteDir()

        then:
        visitedFiles.size() == 3
        visitedFiles.each {
            assert !it.isDirectory() && it.getSize() == 5 && it.getLastModified() >= minimumTimestamp
        }
        visitedDirectories.size() == 2
        visitedDirectories.every {
            it.isDirectory()
        }
    }

    def "directory snapshotter returns the same details as directory walker"() {
        given:
        def rootDir = tmpDir.createDir("root")
        generateFilesAndSubDirectories(rootDir, 10, 5, 3, 1, new AtomicInteger(0))
        MirrorUpdatingDirectoryWalker directorySnapshotter = new MirrorUpdatingDirectoryWalker(TestFiles.fileHasher(), TestFiles.fileSystem(), new StringInterner())
        def patternSet = Mock(PatternSet)
        List<FileVisitDetails> visitedWithJdk7Walker = walkFiles(rootDir, new Jdk7DirectoryWalker(TestFiles.fileSystem()))
        Spec<FileTreeElement> assertingSpec = new Spec<FileTreeElement>() {
            @Override
            boolean isSatisfiedBy(FileTreeElement element) {
                def elementFromFileWalker = visitedWithJdk7Walker.find { it.file == element.file }
                assert elementFromFileWalker != null
                assert element.directory == elementFromFileWalker.directory
                assert element.lastModified == elementFromFileWalker.lastModified
                assert element.size == elementFromFileWalker.size
                assert element.name == elementFromFileWalker.name
                assert element.path == elementFromFileWalker.path
                assert element.relativePath == elementFromFileWalker.relativePath
                assert element.mode == elementFromFileWalker.mode
                visitedWithJdk7Walker.remove(elementFromFileWalker)
                return true
            }
        }

        when:
        directorySnapshotter.walkDir(rootDir.absolutePath, patternSet, new MutableBoolean())
        then:
        1 * patternSet.getAsSpec() >> assertingSpec

        visitedWithJdk7Walker.empty
    }

    private static MirrorUpdatingDirectoryWalker directorySnapshotter() {
        new MirrorUpdatingDirectoryWalker(TestFiles.fileHasher(), TestFiles.fileSystem(), new StringInterner())
    }

    private List<String> walkDirForPaths(DirectoryWalker walkerInstance, File rootDir, PatternSet patternSet) {
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor
        def fileTree = new DirectoryFileTree(rootDir, patternSet, { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        fileTree.visit(fileVisitor)
        return visited
    }

    private List<String> walkDirForPaths(MirrorUpdatingDirectoryWalker walker, File rootDir, PatternSet patternSet) {
        def snapshot = walker.walkDir(rootDir.absolutePath, patternSet, new MutableBoolean())
        def visited = []
        snapshot.accept(new PhysicalSnapshotVisitor() {
            private boolean root = true

            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                if (!root) {
                    visited << directorySnapshot.absolutePath
                }
                root = false
                return true
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                visited << fileSnapshot.absolutePath
            }

            @Override
            void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {

            }
        })
        return visited
    }
}
