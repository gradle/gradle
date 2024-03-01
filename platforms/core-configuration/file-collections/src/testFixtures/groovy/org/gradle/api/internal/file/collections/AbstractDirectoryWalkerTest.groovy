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

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.SetSystemProperties
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

@UsesNativeServices
abstract class AbstractDirectoryWalkerTest<T> extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @Rule
    SetSystemProperties setSystemPropertiesRule

    protected abstract List<T> getWalkers()

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
        walkerInstance << walkers
    }

    @VisibleForTesting
    static generateFilesAndSubDirectories(File parentDir, int fileCount, int dirCount, int maxDepth, int currentDepth, AtomicInteger fileIdGenerator) {
        for (int i = 0; i < fileCount; i++) {
            def file = new File(parentDir, "file" + fileIdGenerator.incrementAndGet())
            file << ("x" * fileIdGenerator.get())
        }
        if (currentDepth < maxDepth) {
            for (int i = 0; i < dirCount; i++) {
                File subDir = new File(parentDir, "dir" + fileIdGenerator.incrementAndGet())
                subDir.mkdirs()
                generateFilesAndSubDirectories(subDir, fileCount, dirCount, maxDepth, currentDepth + 1, fileIdGenerator)
            }
        }
    }

    @Requires(UnitTestPreconditions.Symlinks)
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
        walkerInstance << walkers
    }

    @Requires(UnitTestPreconditions.Symlinks)
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
        walkerInstance << walkers
    }

    @Issue("GRADLE-3400")
    @Requires(UnitTestPreconditions.Symlinks)
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
        walkerInstance << walkers
    }

    def "file walker sees a snapshot of file metadata even if files are deleted after walking has started"() {
        given:
        def rootDir = tmpDir.createDir("root")
        long minimumTimestamp = (System.currentTimeMillis() / 1000 * 1000) - 2000
        def file1 = rootDir.createFile("a/b/1.txt")
        file1 << '12345'
        def file2 = rootDir.createFile("a/b/2.txt")
        file2 << '12345'
        def file3 = rootDir.createFile("a/b/3.txt")
        file3 << '12345'
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), NativeServicesTestFixture.getInstance().get(FileSystem), false)
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

    protected abstract List<String> walkDirForPaths(T walkerInstance, File rootDir, PatternSet patternSet)
}
