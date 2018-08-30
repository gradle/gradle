/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DefaultDirectoryWalkerTest extends AbstractProjectBuilderSpec {
    def directoryWalkerFactory = new Factory<DirectoryWalker>() {
        def directoryWalker = new DefaultDirectoryWalker(TestFiles.fileSystem())

        @Override
        public DirectoryWalker create() {
            return directoryWalker
        }
    }
    private TestVisitor visitor

    def setup () {
        visitor = new TestVisitor()
    }

    def rootDirEmpty() {
        given:
        def root = temporaryFolder.createDir("root")
        def fileTree = new DirectoryFileTree(root, new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    def testUsesSpecFromPatternSetToMatchFilesAndDirs() {
        given:
        def spec = Mock(Spec)
        def patternSet = Mock(PatternSet)
        def fileTree = new DirectoryFileTree(new File("root"), patternSet, directoryWalkerFactory, TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        1 * patternSet.getAsSpec() >> spec
        visitor.assertExpectations()
    }

    def walkSingleFile() {
        given:
        def root = temporaryFolder.createDir("root")
        def fileToCopy = root.createFile("file.txt")
        def fileTree = new DirectoryFileTree(fileToCopy, new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false)

        when:
        visitor.setExpectedVisitations([[fileToCopy]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    /*
    file structure:
    root
        rootFile1
        dir1
           dirFile1
           dirFile2
        rootFile2

        Test that the files are really walked breadth first
     */
    def walkBreadthFirst() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false)

        when:
        visitor.setExpectedVisitations([[rootFile1, rootFile2], [dir1], [dirFile1, dirFile2]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    def walkDepthFirst() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false).postfix()

        when:
        visitor.setExpectedVisitations([[rootFile1, rootFile2], [dirFile2, dirFile1], [dir1]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    def canApplyFilter() {
        given:
        def root = temporaryFolder.createDir("root")
        root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        root.createFile("rootFile2")

        and:
        def patterns = new PatternSet()
        patterns.include("**/*2")
        def filter = new PatternSet()
        filter.include("dir1/**")

        and:
        DirectoryFileTree fileTree = new DirectoryFileTree(root, patterns, directoryWalkerFactory, TestFiles.fileSystem(), false).filter(filter)

        when:
        visitor.setExpectedVisitations([[dir1], [dirFile2]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    def visitorCanStopVisit() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        dir1.createDir("dir1Dir").createFile("dir1Dir1File1")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false)

        when:
        visitor.setStopOn(rootFile1)
        visitor.setExpectedVisitations([[rootFile1, rootFile2]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()

        when:
        visitor = new TestVisitor()
        visitor.setStopOn(dirFile1)
        visitor.setExpectedVisitations([[rootFile1, rootFile2], [dir1], [dirFile2, dirFile1]])
        fileTree.visit(visitor)

        then:
        visitor.assertExpectations()
    }

    def canTestForFileMembership() {
        given:
        def rootDir = temporaryFolder.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = temporaryFolder.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        when:
        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")
        def fileTree = new DirectoryFileTree(rootDir, patterns, directoryWalkerFactory, TestFiles.fileSystem(), false)

        then:
        fileTree.contains(rootTextFile)
        fileTree.contains(nestedTextFile)
        !fileTree.contains(notTextFile)
        !fileTree.contains(excludedFile)
        !fileTree.contains(notUnderRoot)
        !fileTree.contains(doesNotExist)
    }

    def hasUsefulDisplayName() {
        given:
        def treeWithNoIncludesOrExcludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), new PatternSet(), directoryWalkerFactory, TestFiles.fileSystem(), false)
        def includesOnly = new PatternSet()
        includesOnly.include("a/b", "c")
        def treeWithIncludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), includesOnly, directoryWalkerFactory, TestFiles.fileSystem(), false)
        def excludesOnly = new PatternSet()
        excludesOnly.exclude("a/b", "c")
        def treeWithExcludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), excludesOnly, directoryWalkerFactory, TestFiles.fileSystem(), false)

        expect:
        treeWithNoIncludesOrExcludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}'".toString()
        treeWithIncludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}' include 'a/b', 'c'".toString()
        treeWithExcludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}' exclude 'a/b', 'c'".toString()
    }

    private static class TestVisitor implements FileVisitor {
        // This is a list of lists. We want to confirm that files at each level
        // of the directory walk are visited while not caring about their
        // order.
        def expectedVisitations = []
        private stopOn = null

        @Override
        void visitDir(FileVisitDetails dirDetails) {
            handleDetails(dirDetails)
        }

        @Override
        void visitFile(FileVisitDetails fileDetails) {
            handleDetails(fileDetails)
        }

        private void handleDetails(FileVisitDetails details) {
            def file = details.getFile()
            assert expectedVisitations[0].contains(file)
            expectedVisitations[0].remove(file)
            if (expectedVisitations[0].isEmpty() || file == stopOn) {
                expectedVisitations.remove(0)
                if (file == stopOn) {
                    details.stopVisiting()
                }
            }
        }

        void setStopOn(File stop) {
            stopOn = stop
        }

        boolean assertExpectations() {
            assert expectedVisitations.isEmpty()
            return true
        }
    }
}
