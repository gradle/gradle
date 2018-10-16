/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.file.collections

import com.google.common.base.Charsets
import org.gradle.api.JavaVersion
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.jdk7.Jdk7DirectoryWalker
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.UsesNativeServices
import spock.lang.Issue

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

@UsesNativeServices
class DirectoryWalkerTest extends AbstractDirectoryWalkerTest<DirectoryWalker> {
    @Override
    protected List<DirectoryWalker> getWalkers() {
        return [new DefaultDirectoryWalker(), new Jdk7DirectoryWalker(), new ReproducibleDirectoryWalker()]
    }

    // java.nio2 cannot access files with unicode characters when using single-byte non-unicode platform encoding
    // bug seems to show up only on JDK7 when file.encoding != sun.jnu.encoding
    @Issue("GRADLE-2181")
    @Requires(adhoc = { JavaVersion.current().isJava7() && (System.getProperty("sun.jnu.encoding") == null || Charset.forName(System.getProperty("sun.jnu.encoding")).contains(Charsets.UTF_8)) })
    def "check that JDK7 walker gets picked with Unicode encoding as default"() {
        setup:
        System.setProperty("file.encoding", fileEncoding)
        Charset.defaultCharset = null
        def directoryWalkerFactory = TestFiles.directoryFileTreeFactory().create(tmpDir.createDir("root")).directoryWalkerFactory
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

    @Override
    protected List<String> walkDirForPaths(DirectoryWalker walkerInstance, File rootDir, PatternSet patternSet) {
        def visited = []
        def visitClosure = { visited << it.file.absolutePath }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor
        def fileTree = new DirectoryFileTree(rootDir, patternSet, { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        fileTree.visit(fileVisitor)
        return visited
    }
}
