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
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.fingerprint.impl.PatternSetSnapshottingFilter
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.internal.snapshot.SnapshottingFilter

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

class DirectorySnapshotterAsDirectoryWalkerTest extends AbstractDirectoryWalkerTest<DirectorySnapshotter> {
    Consumer<FileSystemLocationSnapshot> completeSnapshotConsumer = Stub()

    def "directory snapshotter returns the same details as directory walker"() {
        given:
        def rootDir = tmpDir.createDir("root")
        generateFilesAndSubDirectories(rootDir, 10, 5, 3, 1, new AtomicInteger(0))
        def patternSet = Mock(PatternSet)
        List<FileVisitDetails> visitedWithJdk7Walker = walkFiles(rootDir)
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
        directorySnapshotter().snapshot(rootDir.absolutePath, directoryWalkerPredicate(patternSet), new AtomicBoolean(), completeSnapshotConsumer)
        then:
        1 * patternSet.getAsSpec() >> assertingSpec

        visitedWithJdk7Walker.empty
    }

    @Override
    protected List<DirectorySnapshotter> getWalkers() {
        [
            directorySnapshotter()
        ]
    }

    private DirectorySnapshotter directorySnapshotter() {
        new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner(), [], Stub(DirectorySnapshotterStatistics.Collector))
    }

    private static List<FileVisitDetails> walkFiles(rootDir) {
        def fileTree = new DirectoryFileTree(rootDir, new PatternSet(), TestFiles.fileSystem(), false)
        def visited = []
        def visitClosure = { visited << it }
        def fileVisitor = [visitFile: visitClosure, visitDir: visitClosure] as FileVisitor
        fileTree.visit(fileVisitor)
        visited
    }

    @Override
    protected List<String> walkDirForPaths(DirectorySnapshotter walker, File rootDir, PatternSet patternSet) {
        def snapshot = walker.snapshot(rootDir.absolutePath, directoryWalkerPredicate(patternSet), new AtomicBoolean(), completeSnapshotConsumer)
        return SnapshotVisitorUtil.getAbsolutePaths(snapshot)
    }

    private static SnapshottingFilter.DirectoryWalkerPredicate directoryWalkerPredicate(PatternSet patternSet) {
        return new PatternSetSnapshottingFilter(patternSet, TestFiles.fileSystem()).asDirectoryWalkerPredicate
    }
}
