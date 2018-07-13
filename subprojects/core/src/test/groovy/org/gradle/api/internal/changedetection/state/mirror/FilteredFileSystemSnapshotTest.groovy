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

package org.gradle.api.internal.changedetection.state.mirror

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class FilteredFileSystemSnapshotTest extends AbstractProjectBuilderSpec {

    FileSystemSnapshotter snapshotter
    DirectoryFileTreeFactory directoryFileTreeFactory = TestFiles.directoryFileTreeFactory()
    FileSystem fileSystem = TestFiles.fileSystem()


    def setup() {
        StringInterner interner = Mock(StringInterner) {
            intern(_) >> { String string -> string }
        }

        snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), interner, fileSystem, directoryFileTreeFactory, new DefaultFileSystemMirror(new DefaultWellKnownFileLocations([])))
    }

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

        def unfiltered = snapshotter.snapshotDirectoryTree(directoryFileTreeFactory.create(root))

        expect:
        filteredPaths(unfiltered, include("**/*2")) == [root, dir1, dirFile2, subdir, rootFile2] as Set
        filteredPaths(unfiltered, include("dir1/**")) == [root, dir1, dirFile1, dirFile2, subdir, subdirFile1] as Set
        filteredPaths(unfiltered, include("*/subdir/**")) == [root, dir1, subdir, subdirFile1] as Set
        filteredPaths(unfiltered, include("dir1/dirFile1")) == [root, dir1, dirFile1] as Set
    }

    private Set<File> filteredPaths(FileSystemSnapshot unfiltered, PatternSet patterns) {
        def result = [] as Set
        new FilteredFileSystemSnapshot(patterns.asSpec, unfiltered, fileSystem).accept(new PhysicalSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                result << new File(directorySnapshot.absolutePath)
                return true
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                result << new File(fileSnapshot.absolutePath)
            }

            @Override
            void postVisitDirectory() {
            }
        })
        return result
    }

    private static PatternSet include(String pattern) {
        new PatternSet().include(pattern)
    }
}
