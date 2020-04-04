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

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.MutableReference
import org.gradle.internal.fingerprint.impl.PatternSetSnapshottingFilter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class FileSystemSnapshotFilterTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    VirtualFileSystem virtualFileSystem = TestFiles.virtualFileSystem()
    FileSystem fileSystem = TestFiles.fileSystem()

    def "filters correctly"() {
        given:
        def root = temporaryFolder.createDir("root")
        root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        def dirFile1 = dir1.createFile("dirFile1")
        def dirFile2 = dir1.createFile("dirFile2")
        def rootFile2 = root.createFile("rootFile2")
        def subdir = dir1.createDir("subdir")
        def subdirFile1 = subdir.createFile("subdirFile1")

        MutableReference<CompleteFileSystemLocationSnapshot> result = MutableReference.empty()
        virtualFileSystem.read(root.getAbsolutePath(), snapshottingFilter(new PatternSet()), result.&set)
        def unfiltered = result.get()

        expect:
        filteredPaths(unfiltered, include("**/*2")) == [root, dir1, dirFile2, subdir, rootFile2] as Set
        filteredPaths(unfiltered, include("dir1/**")) == [root, dir1, dirFile1, dirFile2, subdir, subdirFile1] as Set
        filteredPaths(unfiltered, include("*/subdir/**")) == [root, dir1, subdir, subdirFile1] as Set
        filteredPaths(unfiltered, include("dir1/dirFile1")) == [root, dir1, dirFile1] as Set
    }

    def "filters empty tree"() {
        expect:
        FileSystemSnapshotFilter.filterSnapshot(snapshottingFilter(include("**/*")).asSnapshotPredicate, FileSystemSnapshot.EMPTY) == FileSystemSnapshot.EMPTY
    }

    def "root directory is always matched"() {
        def root = temporaryFolder.createFile("root")

        expect:
        filteredPaths(new CompleteDirectorySnapshot(root.absolutePath, root.name, [], HashCode.fromInt(789)), include("different")) == [root] as Set
    }

    def "root file can be filtered"() {
        def root = temporaryFolder.createFile("root")
        def regularFileSnapshot = new RegularFileSnapshot(root.absolutePath, root.name, HashCode.fromInt(1234), new FileMetadata(5, 1234))

        expect:
        filteredPaths(regularFileSnapshot, include("different")) == [] as Set
        filteredPaths(regularFileSnapshot, include(root.name)) == [root] as Set
    }

    def "returns original tree if nothing is excluded"() {
        def root = temporaryFolder.createDir("root")
        root.createFile("rootFile1")
        def dir1 = root.createDir("dir1")
        dir1.createFile("dirFile1")
        dir1.createFile("dirFile2")

        MutableReference<CompleteFileSystemLocationSnapshot> result = MutableReference.empty()
        virtualFileSystem.read(root.getAbsolutePath(), snapshottingFilter(new PatternSet()), result.&set)
        def unfiltered = result.get()

        expect:
        FileSystemSnapshotFilter.filterSnapshot(snapshottingFilter(include("**/*File*")).asSnapshotPredicate, unfiltered).is(unfiltered)
    }

    private Set<File> filteredPaths(FileSystemSnapshot unfiltered, PatternSet patterns) {
        def result = [] as Set
        FileSystemSnapshotFilter.filterSnapshot(snapshottingFilter(patterns).asSnapshotPredicate, unfiltered).accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                result << new File(directorySnapshot.absolutePath)
                return true
            }

            @Override
            void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                result << new File(fileSnapshot.absolutePath)
            }

            @Override
            void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            }
        })
        return result
    }

    private static PatternSet include(String pattern) {
        new PatternSet().include(pattern)
    }

    private SnapshottingFilter snapshottingFilter(PatternSet patternSet) {
        return new PatternSetSnapshottingFilter(patternSet, fileSystem)
    }
}
