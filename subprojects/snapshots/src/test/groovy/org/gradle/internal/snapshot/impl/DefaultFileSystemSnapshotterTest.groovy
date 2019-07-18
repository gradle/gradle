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

package org.gradle.internal.snapshot.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Predicate

class DefaultFileSystemSnapshotterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileHasher = new TestFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), fileSystemMirror)

    def "fetches details of a file and caches the result"() {
        def f = tmpDir.createFile("f")
        expect:
        def snapshot = snapshotter.snapshot(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.RegularFile
        def stat = TestFiles.fileSystem().stat(f)
        snapshot.isContentAndMetadataUpToDate(new RegularFileSnapshot(f.path, f.absolutePath, fileHasher.hash(f), new FileMetadata(stat.length, stat.lastModified)))

        def snapshot2 = snapshotter.snapshot(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshot(d)
        snapshot.absolutePath == d.path
        snapshot.name == "d"
        snapshot.type == FileType.Directory

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a missing file and caches the result"() {
        def f = tmpDir.file("f")

        expect:
        def snapshot = snapshotter.snapshot(f)
        snapshot.absolutePath == f.path
        snapshot.name == "f"
        snapshot.type == FileType.Missing

        def snapshot2 = snapshotter.snapshot(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory hierarchy and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshot(d)
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of an empty directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshot(d)
        getSnapshotInfo(snapshot) == [d.absolutePath, 1]

        def snapshot2 = snapshotter.snapshot(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory tree with no patterns and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)
        getSnapshotInfo(snapshot) == [d.path, 5]

        def snapshot2 = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)
        snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)
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
        def patterns = new FileNameFilter({ it.endsWith('1') })

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d, patterns)
        getSnapshotInfo(snapshot) == [d.path, 6]

        def snapshot2 = snapshotter.snapshotDirectoryTree(d, patterns)
        !snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)
        !snapshot3.is(snapshot)
        getSnapshotInfo(snapshot3) == [d.path, 8]

        def snapshot4 = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)
        !snapshot4.is(snapshot)
        snapshot4.is(snapshot3)
    }

    def "reuses cached unfiltered trees when looking for details of a filtered tree"() {
        given: "An existing snapshot"
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createFile("d1/f1")
        snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)

        and: "A filtered tree over the same directory"
        def patterns = new FileNameFilter({ it.endsWith('1') })

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(d, patterns)
        def relativePaths = [] as Set
        snapshot.accept(new FileSystemSnapshotVisitor() {
            private Deque<String> relativePath = new ArrayDeque<String>()
            private boolean seenRoot = false

            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (!seenRoot) {
                    seenRoot = true
                } else {
                    relativePath.addLast(directorySnapshot.name)
                    relativePaths.add(relativePath.join("/"))
                }
                return true
            }

            @Override
            void visitFile(FileSystemLocationSnapshot fileSnapshot) {
                relativePath.addLast(fileSnapshot.name)
                relativePaths.add(relativePath.join("/"))
                relativePath.removeLast()
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
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
        def snapshot = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)

        then:
        getSnapshotInfo(snapshot) == [null, 0]
    }

    def "snapshots file as directory tree"() {
        given:
        def d = tmpDir.createFile("fileAsTree")

        when:
        def snapshot = snapshotter.snapshotDirectoryTree(d, SnapshottingFilter.EMPTY)

        then:
        getSnapshotInfo(snapshot) == [null, 1]
        snapshot.accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                throw new UnsupportedOperationException()
            }

            @Override
            void visitFile(FileSystemLocationSnapshot fileSnapshot) {
                assert fileSnapshot.absolutePath == d.getAbsolutePath()
                assert fileSnapshot.name == d.name
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                throw new UnsupportedOperationException()
            }
        })
    }

    void assertSingleFileSnapshot(snapshots) {
        assert snapshots.size() == 1
        assert getSnapshotInfo(snapshots[0]) == [null, 1]
    }

    private static List getSnapshotInfo(FileSystemSnapshot tree) {
        String rootPath = null
        int count = 0
        tree.accept(new FileSystemSnapshotVisitor() {
            @Override
            boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                if (rootPath == null) {
                    rootPath = directorySnapshot.absolutePath
                }
                count++
                return true
            }

            @Override
            void visitFile(FileSystemLocationSnapshot fileSnapshot) {
                count++
            }

            @Override
            void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            }
        })
        return [rootPath, count]
    }

    private static class FileNameFilter implements SnapshottingFilter {
        private final Predicate<String> predicate

        FileNameFilter(Predicate<String> predicate) {
            this.predicate = predicate
        }

        @Override
        boolean isEmpty() {
            return false
        }

        @Override
        FileSystemSnapshotPredicate getAsSnapshotPredicate() {
            return new FileSystemSnapshotPredicate() {
                @Override
                boolean test(FileSystemLocationSnapshot fileSystemLocationSnapshot, Iterable<String> relativePath) {
                    return fileSystemLocationSnapshot.getType() == FileType.Directory || predicate.test(fileSystemLocationSnapshot.name)
                }
            }
        }

        @Override
        DirectoryWalkerPredicate getAsDirectoryWalkerPredicate() {
            return new DirectoryWalkerPredicate() {
                @Override
                boolean test(Path path, String name, boolean isDirectory, @Nullable BasicFileAttributes attrs, Iterable<String> relativePath) {
                    return isDirectory || predicate.test(name)
                }
            }
        }
    }
}
