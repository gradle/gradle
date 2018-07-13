/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.TestFileHasher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultFileSystemSnapshotterTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory(), fileSystemMirror)

    def "fetches details of a file and caches the result"() {
        def f = tmpDir.createFile("f")

        expect:
        def snapshot = snapshotter.snapshotSelf(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.RegularFile
        snapshot.isContentAndMetadataUpToDate(new PhysicalFileSnapshot(f.path, f.absolutePath, fileHasher.hash(f), TestFiles.fileSystem().stat(f).lastModified))

        def snapshot2 = snapshotter.snapshotSelf(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotSelf(d)
        snapshot.absolutePath == d.path
        snapshot.name == "d"
        snapshot.type == FileType.Directory

        def snapshot2 = snapshotter.snapshotSelf(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a missing file and caches the result"() {
        def f = tmpDir.file("f")

        expect:
        def snapshot = snapshotter.snapshotSelf(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.Missing

        def snapshot2 = snapshotter.snapshotSelf(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory hierarchy and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(dirTree(d))
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshotDirectoryTree(dirTree(d))
        snapshot2.is(snapshot)
    }

    def "fetches details of an empty directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(dirTree(d))
        getSnapshotInfo(snapshot) == [d.absolutePath, 1]

        def snapshot2 = snapshotter.snapshotDirectoryTree(dirTree(d))
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory tree with no patterns and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")
        def tree = dirTree(d)

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(tree)
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(dirTree(d))
        snapshot3.is(snapshot)
    }

    def "fetches details of a directory tree with patterns patterns and does not cache the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createFile("d1/f1")
        d.createDir("d2")
        d.createFile("d2/f1")
        d.createFile("d2/f2")
        def patterns = TestFiles.patternSetFactory.create()
        patterns.include "**/*1"
        def tree = TestFiles.directoryFileTreeFactory().create(d, patterns)

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(tree)
        getSnapshotInfo(snapshot) == [d.path, 6]

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        !snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(dirTree(d))
        !snapshot3.is(snapshot)
        getSnapshotInfo(snapshot3) == [d.path, 8]

        def snapshot4 = snapshotter.snapshotDirectoryTree(dirTree(d))
        !snapshot4.is(snapshot)
        snapshot4.is(snapshot3)
    }

    def "reuses cached unfiltered trees when looking for details of a filtered tree"() {
        given: "An existing snapshot"
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createFile("d1/f1")
        def unfilteredTree = dirTree(d)
        snapshotter.snapshotDirectoryTree(unfilteredTree)

        and: "A filtered tree over the same directory"
        def patterns = TestFiles.patternSetFactory.create()
        patterns.include "**/*1"
        DirectoryFileTree filteredTree = Mock(DirectoryFileTree) {
            getDir() >> d
            getPatterns() >> patterns
        }

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(filteredTree)
        def relativePaths = [] as Set
        snapshot.accept(new PhysicalSnapshotVisitor() {
            private Deque<String> relativePath = new ArrayDeque<String>()
            private boolean seenRoot = false

            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                if (!seenRoot) {
                    seenRoot = true
                } else {
                    relativePath.addLast(directorySnapshot.name)
                    relativePaths.add(relativePath.join("/"))
                }
                return true
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                relativePath.addLast(fileSnapshot.name)
                relativePaths.add(relativePath.join("/"))
                relativePath.removeLast()
            }

            @Override
            void postVisitDirectory() {
                if (relativePath.isEmpty()) {
                    seenRoot = false
                } else {
                    relativePath.removeLast()
                }
            }
        })

        then: "The filtered tree uses the cached state"
        relativePaths == ["d1", "d1/f1", "f1"] as Set
    }

    def "snapshots a non-existing directory"() {
        given:
        def d = tmpDir.file("dir")

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(dirTree(d))

        then:
        getSnapshotInfo(snapshot) == [null, 0]
    }

    def "snapshots file as directory tree"() {
        given:
        def d = tmpDir.createFile("fileAsTree")

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(dirTree(d))

        then:
        getSnapshotInfo(snapshot) == [null, 1]
        snapshot.accept(new PhysicalSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                throw new UnsupportedOperationException()
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                assert fileSnapshot.absolutePath == d.getAbsolutePath()
                assert fileSnapshot.name == d.name
            }

            @Override
            void postVisitDirectory() {
                throw new UnsupportedOperationException()
            }
        })
    }

    def "snapshots a file and caches the result"() {
        def f = tmpDir.createFile("f")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputChanged()
        f << "some other content"

        def snapshot2 = snapshotter.snapshotAll(f)
        snapshot != snapshot2
    }

    def "snapshots a missing file and caches the result"() {
        def f = tmpDir.file("missing")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputChanged()
        f.createDir()

        def snapshot2 = snapshotter.snapshotAll(f)
        snapshot != snapshot2
    }

    def "snapshots a directory tree and caches the result"() {
        def f = tmpDir.createDir("dir")
        f.createFile("child1/f")
        f.createFile("child2/f")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputChanged()
        f.createFile("newFile")

        def snapshot2 = snapshotter.snapshotAll(f)
        snapshot != snapshot2
    }

    def "determines whether file exists when snapshot is cached"() {
        def f = tmpDir.createFile("file")
        def d = tmpDir.createDir("dir")
        def m = tmpDir.file("missing")

        given:
        snapshotter.snapshotSelf(f)
        snapshotter.snapshotSelf(d)
        snapshotter.snapshotSelf(m)

        expect:
        snapshotter.exists(f)
        snapshotter.exists(d)
        !snapshotter.exists(m)
    }

    def "determines whether file exists when snapshot is not cached"() {
        def f = tmpDir.createFile("file")
        def d = tmpDir.createDir("dir")
        def m = tmpDir.file("missing")

        expect:
        snapshotter.exists(f)
        snapshotter.exists(d)
        !snapshotter.exists(m)
    }

    private static DirectoryFileTree dirTree(File dir) {
        TestFiles.directoryFileTreeFactory().create(dir)
    }

    private static List getSnapshotInfo(FileSystemSnapshot tree) {
        String rootPath = null
        int count = 0
        tree.accept(new PhysicalSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                if (rootPath == null) {
                    rootPath = directorySnapshot.absolutePath
                }
                count++
                return true
            }

            @Override
            void visit(PhysicalSnapshot fileSnapshot) {
                count++
            }

            @Override
            void postVisitDirectory() {
            }
        })
        return [rootPath, count]
    }
}
