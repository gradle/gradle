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

package org.gradle.internal.snapshot.impl

import org.gradle.api.file.FileTreeElement
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.AbstractDirectoryWalkerTest
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.api.internal.file.collections.jdk7.Jdk7DirectoryWalker
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.Factory
import org.gradle.internal.MutableBoolean
import org.gradle.internal.snapshot.BrokenLinkSnapshot
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class DirectorySnapshotterAsDirectoryWalkerTest extends AbstractDirectoryWalkerTest<DirectorySnapshotter> {
    def "directory snapshotter returns the same details as directory walker"() {
        given:
        def rootDir = tmpDir.createDir("root")
        generateFilesAndSubDirectories(rootDir, 10, 5, 3, 1, new AtomicInteger(0))
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
        directorySnapshotter().snapshot(rootDir.absolutePath, patternSet, new MutableBoolean())
        then:
        1 * patternSet.getAsSpec() >> assertingSpec

        visitedWithJdk7Walker.empty
    }

    @Requires(TestPrecondition.SYMLINKS)
    @Unroll
    def "missing symbolic link gets added as BrokenLinkSnapshot - walker: #walkerInstance.class.simpleName"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def dir = rootDir.createDir("target")
        def link = rootDir.file("source")
        link.createLink(dir)

        when:
        dir.deleteDir()
        def visited = walkDirForPaths0(walkerInstance, rootDir, new PatternSet())

        then:
        visited.size() == 1
        visited[0] instanceof BrokenLinkSnapshot

        cleanup:
        link.delete()

        where:
        walkerInstance << walkers
    }

    @Override
    protected List<DirectorySnapshotter> getWalkers() {
        [
            directorySnapshotter()
        ]
    }

    private static DirectorySnapshotter directorySnapshotter() {
        new DirectorySnapshotter(TestFiles.fileHasher(), TestFiles.fileSystem(), new StringInterner())
    }

    private static List<FileVisitDetails> walkFiles(rootDir, walkerInstance) {
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), { walkerInstance } as Factory, TestFiles.fileSystem(), false)
        def visited = []
        def visitClosure = { visited << it }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor
        fileTree.visit(fileVisitor)
        visited
    }

    @Override
    protected List<String> walkDirForPaths(DirectorySnapshotter walker, File rootDir, PatternSet patternSet) {
        return walkDirForPaths0(walker, rootDir, patternSet).collect { it.absolutePath }
    }

    @Override
    protected boolean enableMissingLinkTest() {
        return false
    }

    private List<FileSystemLocationSnapshot> walkDirForPaths0(DirectorySnapshotter walker, File rootDir, PatternSet patternSet) {
        def snapshot = walker.snapshot(rootDir.absolutePath, patternSet, new MutableBoolean())
        def visited = []
        snapshot.accept(new FileSystemSnapshotVisitor() {
            private boolean root = true

            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (!root) {
                    visited << directorySnapshot
                }
                root = false
                return true
            }

            @Override
            void visit(FileSystemLocationSnapshot fileSnapshot) {
                visited << fileSnapshot
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            }
        })
        return visited
    }

}
