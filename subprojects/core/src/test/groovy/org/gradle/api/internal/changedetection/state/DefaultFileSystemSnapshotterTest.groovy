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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.hash.DefaultFileHasher
import org.gradle.internal.nativeintegration.filesystem.FileType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultFileSystemSnapshotterTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileHasher = new DefaultFileHasher()
    def cacheDir = tmpDir.createDir("cached-things")
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory(), new DefaultFileSystemMirror([]))

    def "snapshots a file and caches the result"() {
        def f = tmpDir.createFile("f")

        expect:
        def snapshot = snapshotter.snapshotFile(f)
        snapshot.path == f.path
        snapshot.name == "f"
        snapshot.type == FileType.RegularFile
        snapshot.root
        snapshot.relativePath.toString() == "f"
        snapshot.content == new FileHashSnapshot(fileHasher.hash(f), f.lastModified())

        def snapshot2 = snapshotter.snapshotFile(f)
        snapshot2.is(snapshot)
    }

    def "snapshots a directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotFile(d)
        snapshot.path == d.path
        snapshot.name == "d"
        snapshot.type == FileType.Directory
        snapshot.root
        snapshot.relativePath.toString() == "d"
        snapshot.content == DirSnapshot.instance

        def snapshot2 = snapshotter.snapshotFile(d)
        snapshot2.is(snapshot)
    }

    def "snapshots a missing file and caches the result"() {
        def f = tmpDir.file("f")

        expect:
        def snapshot = snapshotter.snapshotFile(f)
        snapshot.path == f.path
        snapshot.name == "f"
        snapshot.type == FileType.Missing
        snapshot.root
        snapshot.relativePath.toString() == "f"
        snapshot.content == MissingFileSnapshot.instance

        def snapshot2 = snapshotter.snapshotFile(f)
        snapshot2.is(snapshot)
    }

    def "snapshots a directory hierarchy and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d)
        snapshot.path == d.path
        snapshot.descendents.size() == 4

        def snapshot2 = snapshotter.snapshotDirectoryTree(d)
        snapshot2.is(snapshot)
    }

    def "snapshots an empty directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d)
        snapshot.path == d.path
        snapshot.descendents.empty

        def snapshot2 = snapshotter.snapshotDirectoryTree(d)
        snapshot2.is(snapshot)
    }

    def "snapshots a directory tree with no patterns and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")
        def tree = TestFiles.directoryFileTreeFactory().create(d)

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(tree)
        snapshot.path == d.path
        snapshot.descendents.size() == 4

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d)
        snapshot3.is(snapshot)
    }

    def "snapshots a directory tree with patterns patterns and does not cache the result"() {
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
        snapshot.path == d.path
        snapshot.descendents.size() == 5

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        !snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d)
        !snapshot3.is(snapshot)
        snapshot3.descendents.size() == 7

        def snapshot4 = snapshotter.snapshotDirectoryTree(TestFiles.directoryFileTreeFactory().create(d))
        !snapshot4.is(snapshot)
        snapshot4.is(snapshot3)
    }
}
