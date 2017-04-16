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
import org.gradle.caching.internal.DefaultBuildCacheHasher
import org.gradle.internal.nativeintegration.filesystem.FileType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultFileSystemSnapshotterTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def fileHasher = new DefaultFileHasher()
    def fileSystemMirror = new DefaultFileSystemMirror([])
    def snapshotter = new DefaultFileSystemSnapshotter(fileHasher, new StringInterner(), TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory(), fileSystemMirror)

    def "fetches details of a file and caches the result"() {
        def f = tmpDir.createFile("f")

        expect:
        def snapshot = snapshotter.snapshotSelf(f)
        snapshot.path == f.path
        snapshot.name == "f"
        snapshot.type == FileType.RegularFile
        snapshot.root
        snapshot.relativePath.toString() == "f"
        snapshot.content == new FileHashSnapshot(fileHasher.hash(f), f.lastModified())

        def snapshot2 = snapshotter.snapshotSelf(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotSelf(d)
        snapshot.path == d.path
        snapshot.name == "d"
        snapshot.type == FileType.Directory
        snapshot.root
        snapshot.relativePath.toString() == "d"
        snapshot.content == DirContentSnapshot.instance

        def snapshot2 = snapshotter.snapshotSelf(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a missing file and caches the result"() {
        def f = tmpDir.file("f")

        expect:
        def snapshot = snapshotter.snapshotSelf(f)
        snapshot.path == f.path
        snapshot.name == "f"
        snapshot.type == FileType.Missing
        snapshot.root
        snapshot.relativePath.toString() == "f"
        snapshot.content == MissingFileContentSnapshot.instance

        def snapshot2 = snapshotter.snapshotSelf(f)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory hierarchy and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d)
        snapshot.path == d.path
        snapshot.descendants.size() == 4

        def snapshot2 = snapshotter.snapshotDirectoryTree(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of an empty directory and caches the result"() {
        def d = tmpDir.createDir("d")

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(d)
        snapshot.path == d.path
        snapshot.descendants.empty

        def snapshot2 = snapshotter.snapshotDirectoryTree(d)
        snapshot2.is(snapshot)
    }

    def "fetches details of a directory tree with no patterns and caches the result"() {
        def d = tmpDir.createDir("d")
        d.createFile("f1")
        d.createFile("d1/f2")
        d.createDir("d2")
        def tree = TestFiles.directoryFileTreeFactory().create(d)

        expect:
        def snapshot = snapshotter.snapshotDirectoryTree(tree)
        snapshot.path == d.path
        snapshot.descendants.size() == 4

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d)
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
        snapshot.path == d.path
        snapshot.descendants.size() == 5

        def snapshot2 = snapshotter.snapshotDirectoryTree(tree)
        !snapshot2.is(snapshot)

        def snapshot3 = snapshotter.snapshotDirectoryTree(d)
        !snapshot3.is(snapshot)
        snapshot3.descendants.size() == 7

        def snapshot4 = snapshotter.snapshotDirectoryTree(TestFiles.directoryFileTreeFactory().create(d))
        !snapshot4.is(snapshot)
        snapshot4.is(snapshot3)
    }

    def "snapshots a file and caches the result"() {
        def f = tmpDir.createFile("f")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputsGenerated()
        f << "some other content"

        def snapshot2 = snapshotter.snapshotAll(f)
        hash(snapshot) != hash(snapshot2)
    }

    def "snapshots a missing file and caches the result"() {
        def f = tmpDir.file("missing")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputsGenerated()
        f.createDir()

        def snapshot2 = snapshotter.snapshotAll(f)
        hash(snapshot) != hash(snapshot2)
    }

    def "snapshots a directory tree and caches the result"() {
        def f = tmpDir.createDir("dir")
        f.createFile("child1/f")
        f.createFile("child2/f")

        expect:
        def snapshot = snapshotter.snapshotAll(f)
        snapshotter.snapshotAll(f).is(snapshot)

        fileSystemMirror.beforeTaskOutputsGenerated()
        f.createFile("newFile")

        def snapshot2 = snapshotter.snapshotAll(f)
        hash(snapshot) != hash(snapshot2)
    }

    def hash(Snapshot snapshot) {
        def builder = new DefaultBuildCacheHasher()
        snapshot.appendToHasher(builder)
        return builder.hash()
    }
}
