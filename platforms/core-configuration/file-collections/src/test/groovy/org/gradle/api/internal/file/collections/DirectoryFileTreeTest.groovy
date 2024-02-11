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
import org.gradle.api.file.ReproducibleFileVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class DirectoryFileTreeTest extends AbstractProjectBuilderSpec {
    def "visits structure"() {
        given:
        def root = temporaryFolder.createDir("root")
        def patterns = new PatternSet()
        def owner = Stub(FileTreeInternal)
        def visitor = Mock(MinimalFileTree.MinimalFileTreeStructureVisitor)

        def fileTree = new DirectoryFileTree(root, patterns, TestFiles.fileSystem(), false)

        when:
        fileTree.visitStructure(visitor, owner)

        then:
        1 * visitor.visitFileTree(root, patterns, owner)
        0 * visitor._
    }

    def "root directory is empty - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def root = temporaryFolder.createDir("root")
        def fileTree = new DirectoryFileTree(root, new PatternSet(), TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        visitor.visited == []

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "test uses spec from patternSet to match files and dirs - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def spec = Mock(Spec)
        def patternSet = Mock(PatternSet)
        def fileTree = new DirectoryFileTree(new File("root"), patternSet, TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        1 * patternSet.getAsSpec() >> spec
        visitor.visited == []

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "walk single file - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def root = temporaryFolder.createDir("root")
        def fileToCopy = root.createFile("file.txt")
        def fileTree = new DirectoryFileTree(fileToCopy, new PatternSet(), TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        visitor.visited == [fileToCopy]

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    /**
    file structure:
    root
        rootFile1
        dir1
           dirFile1
           dirFile2
        rootFile2

        Test that the files are really walked breadth first
     */
    def "walk breadth first - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), TestFiles.fileSystem(), false)

        when:
        fileTree.visit(visitor)

        then:
        visitor.visited.sort() == [rootFile1, rootFile2, dir1, dirFile1, dirFile2].sort()

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "walk depth first - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), TestFiles.fileSystem(), false).postfix()

        when:
        fileTree.visit(visitor)

        then:
        visitor.visited.sort() == [rootFile1, rootFile2, dirFile2, dirFile1, dir1].sort()

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "can apply filter - isReproducible: #visitor.isReproducibleFileOrder"() {
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
        DirectoryFileTree fileTree = new DirectoryFileTree(root, patterns, TestFiles.fileSystem(), false).filter(filter)

        when:
        fileTree.visit(visitor)

        then:
        visitor.visited == [dir1, dirFile2]

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "visitor can stop visit - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def root = temporaryFolder.createDir("root")
        def rootFile1 = root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        dir1.createDir("dir1Dir").createFile("dir1Dir1File1")
        def rootFile2 = root.createFile("rootFile2")

        def fileTree = new DirectoryFileTree(root, new PatternSet(), TestFiles.fileSystem(), false)

        when:
        visitor.stopOn = rootFile2
        fileTree.visit(visitor)

        then:
        visitor.visited == [rootFile1, rootFile2]

        when:
        visitor = new TestVisitor(true)
        visitor.stopOn = dirFile1
        fileTree.visit(visitor)

        then:
        visitor.visited == [rootFile1, rootFile2, dir1, dirFile1]

        where:
        visitor << [new TestVisitor(true)] // stopping at a given point assumes the order is fixed, so only reproducible visitor makes sense here
    }

    def "can test for file membership - isReproducible: #visitor.isReproducibleFileOrder"() {
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
        def fileTree = new DirectoryFileTree(rootDir, patterns, TestFiles.fileSystem(), false)

        then:
        fileTree.contains(rootTextFile)
        fileTree.contains(nestedTextFile)
        !fileTree.contains(notTextFile)
        !fileTree.contains(excludedFile)
        !fileTree.contains(notUnderRoot)
        !fileTree.contains(doesNotExist)

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    def "has useful displayName - isReproducible: #visitor.isReproducibleFileOrder"() {
        given:
        def treeWithNoIncludesOrExcludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), new PatternSet(), TestFiles.fileSystem(), false)
        def includesOnly = new PatternSet()
        includesOnly.include("a/b", "c")
        def treeWithIncludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), includesOnly, TestFiles.fileSystem(), false)
        def excludesOnly = new PatternSet()
        excludesOnly.exclude("a/b", "c")
        def treeWithExcludes = new DirectoryFileTree(temporaryFolder.getTestDirectory(), excludesOnly, TestFiles.fileSystem(), false)

        expect:
        treeWithNoIncludesOrExcludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}'".toString()
        treeWithIncludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}' include 'a/b', 'c'".toString()
        treeWithExcludes.getDisplayName() == "directory '${temporaryFolder.getTestDirectory()}' exclude 'a/b', 'c'".toString()

        where:
        visitor << [new TestVisitor(false), new TestVisitor(true)]
    }

    private static class TestVisitor implements ReproducibleFileVisitor {
        def visited = []
        def stopOn = null
        def isReproducibleFileOrder

        TestVisitor(boolean isReproducibleFileOrder) {
            this.isReproducibleFileOrder = isReproducibleFileOrder
        }

        @Override
        void visitDir(FileVisitDetails dirDetails) {
            handleDetails(dirDetails)
        }

        @Override
        void visitFile(FileVisitDetails fileDetails) {
            handleDetails(fileDetails)
        }

        private void handleDetails(FileVisitDetails details) {
            visited += details.getFile()
            if (details.getFile() == stopOn) {
                details.stopVisiting()
            }
        }

        @Override
        boolean isReproducibleFileOrder() {
            isReproducibleFileOrder
        }
    }
}
