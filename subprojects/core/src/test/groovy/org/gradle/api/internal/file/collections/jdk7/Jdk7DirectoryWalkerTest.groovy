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

import org.gradle.api.GradleException
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.collections.DefaultDirectoryWalker
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

@Requires(TestPrecondition.JDK7_OR_LATER)
@UsesNativeServices
class Jdk7DirectoryWalkerTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Shared
    private String originalFileEncoding

    def setupSpec() {
        originalFileEncoding = System.getProperty("file.encoding")
    }

    def cleanup() {
        System.setProperty("file.encoding", originalFileEncoding)
        Charset.defaultCharset = null // clear cache
    }

    // java.nio2 cannot access files with unicode characters when using single-byte non-unicode platform encoding
    @Issue("GRADLE-2181")
    def "check that JDK7 walker gets picked with Unicode encoding as default"() {
        setup:
        System.setProperty("file.encoding", fileEncoding)
        Charset.defaultCharset = null
        def directoryWalkerFactory = new DirectoryFileTree(tmpDir.createDir("root")).directoryWalkerFactory
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

        def fileTree = new DirectoryFileTree(rootDir, patterns, { walkerInstance } as Factory)
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor

        when:
        fileTree.visit(fileVisitor)

        then:
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        !visited.contains(notTextFile.absolutePath)
        !visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker()]
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

    private boolean checkFileVisitDetailsEqual(List<FileVisitDetails> visitedWithDefaultWalker, List<FileVisitDetails> visitedWithJdk7Walker) {
        visitedWithDefaultWalker.every { FileVisitDetails details ->
            def detailsFromJdk7Walker = visitedWithJdk7Walker.find { it.file.absolutePath == details.file.absolutePath }

            detailsFromJdk7Walker != null &&
                details.lastModified == detailsFromJdk7Walker.lastModified &&
                details.directory == detailsFromJdk7Walker.directory &&
                (details.directory || details.size == detailsFromJdk7Walker.size)
        }
    }

    private generateFilesAndSubDirectories(TestFile parentDir, int fileCount, int dirCount, int maxDepth, int currentDepth, AtomicInteger fileIdGenerator) {
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

    private List<FileVisitDetails> walkFiles(rootDir, walkerInstance) {
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory)
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

        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory)
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor

        when:
        fileTree.visit(fileVisitor)

        then:
        visited.contains(file.absolutePath)
        visited.contains(link.file("c.txt").absolutePath)
        link.file("c.txt").text == "Hello world"

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker()]
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

        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory)
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor

        when:
        fileTree.visit(fileVisitor)

        then:
        visited.contains(file.absolutePath)
        visited.contains(link.absolutePath)
        link.text == "Hello world"

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker()]
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "missing symbolic link causes an exception - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("a/b")
        def link = rootDir.file("a/d")
        link.createLink(dir)

        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory)
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor

        when:
        dir.deleteDir()
        fileTree.visit(fileVisitor)

        then:
        GradleException e = thrown()
        e.message.contains("Could not list contents of '${link.absolutePath}'.")

        cleanup:
        link.delete()

        where:
        walkerInstance << [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker()]
    }


    def "file walker sees a snapshot of file metadata even if files are deleted after walking has started"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def minimumTimestamp = (System.currentTimeMillis()/1000 * 1000) - 1000
        def file1 = rootDir.createFile("a/b/1.txt")
        file1 << '12345'
        def file2 = rootDir.createFile("a/b/2.txt")
        file2 << '12345'
        def file3 = rootDir.createFile("a/b/3.txt")
        file3 << '12345'
        def walkerInstance = new Jdk7DirectoryWalker()
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory)
        def visitedFiles = []
        def visitedDirectories = []
        def fileVisitor = [visitFile: { visitedFiles << it }, visitDir: { visitedDirectories << it }] as FileVisitor

        when:
        fileTree.visit(fileVisitor)
        rootDir.deleteDir()

        then:
        visitedFiles.size() == 3
        visitedFiles.every {
            !it.isDirectory() && it.getSize() == 5 && it.getLastModified() >= minimumTimestamp
        }
        visitedDirectories.size() == 2
        visitedDirectories.every {
            it.isDirectory()
        }
    }

}
