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
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.impl.PatternSetSnapshottingFilter
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE

@CleanupTestDirectory
class FileSystemSnapshotFilterTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    FileSystemAccess fileSystemAccess = TestFiles.fileSystemAccess()
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

        def unfiltered = fileSystemAccess.read(root.getAbsolutePath(), snapshottingFilter(new PatternSet())).get()

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
        def unfiltered = new DirectorySnapshot(root.absolutePath, root.name, AccessType.DIRECT, TestHashCodes.hashCodeFrom(789), [])

        expect:
        filteredPaths(unfiltered, include("different")) == [root] as Set
    }

    def "root file can be filtered"() {
        def root = temporaryFolder.createFile("root")
        def regularFileSnapshot = new RegularFileSnapshot(root.absolutePath, root.name, TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(5, 1234, AccessType.DIRECT))

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

        def unfiltered = fileSystemAccess.read(root.getAbsolutePath(), snapshottingFilter(new PatternSet())).get()

        when:
        def filtered = FileSystemSnapshotFilter.filterSnapshot(snapshottingFilter(include("**/*File*")).asSnapshotPredicate, unfiltered)

        then:
        filtered.is(unfiltered)
    }

    private Set<File> filteredPaths(FileSystemSnapshot unfiltered, PatternSet patterns) {
        def result = [] as Set
        def filtered = FileSystemSnapshotFilter.filterSnapshot(snapshottingFilter(patterns).asSnapshotPredicate, unfiltered)
        filtered.accept(new FileSystemSnapshotHierarchyVisitor() {
            SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                result << new File(snapshot.absolutePath)
                return CONTINUE
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
