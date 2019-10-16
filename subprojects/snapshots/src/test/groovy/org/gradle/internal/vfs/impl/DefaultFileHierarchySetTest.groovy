/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class DefaultFileHierarchySetTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    DirectorySnapshotter directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner())

    def "creates from a single file"() {
        def dir = tmpDir.createDir("dir")
        def child = dir.file("child").createFile()

        def dirSnapshot = snapshotDir(dir)
        expect:
        def set = DefaultFileHierarchySet.of(dirSnapshot)
        set.getSnapshot(dir) == dirSnapshot
        assertFileSnapshot(set, child)
        !set.getSnapshot(dir.parentFile)
        !set.getSnapshot(tmpDir.file("dir2"))
        !set.getSnapshot(tmpDir.file("d"))
    }

    def "creates from multiple files"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1Child = dir1.file("child").createFile()
        def dir2 = parent.createDir("common/dir2")
        def dir2Child = dir2.file("child").createFile()
        def dir3 = parent.createDir("common/dir3")
        def dir3Child = dir3.file("child").createFile()
        def dir1Snapshot = snapshotDir(dir1)
        def dir2Snapshot = snapshotDir(dir2)
        def dir3Snapshot = snapshotDir(dir3)

        expect:
        def set = DefaultFileHierarchySet.of([dir1Snapshot, dir2Snapshot, dir3Snapshot])
        [dir1Snapshot, dir2Snapshot, dir3Snapshot].each { FileSystemLocationSnapshot snapshot ->
            assert set.getSnapshot(snapshot.absolutePath) == snapshot
        }
        [dir1Child, dir2Child, dir3Child].each {
            assertFileSnapshot(set, it)
        }
        assertMissingFileSnapshot(set, dir2.file("some/non-existing/file"))
        !set.getSnapshot(parent)
        !set.getSnapshot(dir2.parentFile)
        !set.getSnapshot(tmpDir.file("dir"))
        !set.getSnapshot(tmpDir.file("dir12"))
        !set.getSnapshot(tmpDir.file("common/dir21"))
        set.flatten() == [parent.path, "1:dir1", "1:common", "2:dir2", "2:dir3"]
    }

    def "creates from files where one file is ancestor of the others"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = dir1.createDir("dir2")
        def dir1Child = dir1.file("child")
        dir1Child.createFile()
        def dir2Child = dir2.file("child/some/nested/structure").createFile()

        def dir1Snapshot = snapshotDir(dir1)
        def dir2Snapshot = snapshotDir(dir2)
        expect:
        def set = DefaultFileHierarchySet.of([dir2Snapshot, dir1Snapshot])
        set.getSnapshot(dir1) == dir1Snapshot
        equalSnapshots(set.getSnapshot(dir2), dir2Snapshot)
        assertFileSnapshot(set, dir1Child)
        assertFileSnapshot(set, dir2Child)
        !set.getSnapshot(dir1.parentFile)
        !set.getSnapshot(tmpDir.file("dir"))
        !set.getSnapshot(tmpDir.file("dir12"))
        !set.getSnapshot(tmpDir.file("dir21"))
        set.flatten() == [dir1.path]
    }

    def "can add dir to empty set"() {
        def empty = DefaultFileHierarchySet.of()
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")

        def dir1Snapshot = snapshotDir(dir1)
        def dir2Snapshot = snapshotDir(dir2)
        expect:
        def s1 = empty.plus(dir1Snapshot)
        s1.getSnapshot(dir1) == dir1Snapshot
        !s1.getSnapshot(dir2)

        def s2 = empty.plus(dir2Snapshot)
        !s2.getSnapshot(dir1)
        s2.getSnapshot(dir2) == dir2Snapshot
    }

    def "can add dir to singleton set"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def tooMany = parent.createDir("dir12")
        def tooFew = parent.createDir("dir")
        def child = dir1.createDir("child1")
        def single = DefaultFileHierarchySet.of(snapshotDir(dir1))

        expect:
        def s1 = single.plus(snapshotDir(dir2))
        s1.getSnapshot(dir1)
        s1.getSnapshot(child)
        s1.getSnapshot(dir2)
        !s1.getSnapshot(dir3)
        !s1.getSnapshot(tooFew)
        !s1.getSnapshot(tooMany)
        !s1.getSnapshot(parent)
        s1.flatten() == [parent.path, "1:dir1", "1:dir2"]

        def s2 = single.plus(snapshotDir(dir1))
        s2.getSnapshot(dir1)
        s2.getSnapshot(child)
        !s2.getSnapshot(dir2)
        !s2.getSnapshot(dir3)
        !s2.getSnapshot(tooFew)
        !s2.getSnapshot(tooMany)
        !s2.getSnapshot(parent)
        s2.flatten() == [dir1.path]

        def s3 = single.plus(snapshotDir(child))
        // The parent directory snapshot was removed
        !s3.getSnapshot(dir1)
        s3.getSnapshot(child)
        !s3.getSnapshot(dir2)
        !s3.getSnapshot(dir3)
        !s3.getSnapshot(tooFew)
        !s3.getSnapshot(tooMany)
        !s3.getSnapshot(parent)
        s3.flatten() == [dir1.path, "1:child1"]

        def s4 = single.plus(snapshotDir(parent))
        s4.getSnapshot(dir1)
        s4.getSnapshot(child)
        s4.getSnapshot(dir2)
        s4.getSnapshot(dir3)
        s4.getSnapshot(parent)
        s4.flatten() == [parent.path]

        def s5 = single.plus(snapshotDir(tooFew))
        s5.getSnapshot(dir1)
        s5.getSnapshot(child)
        s5.getSnapshot(tooFew)
        !s5.getSnapshot(dir2)
        !s5.getSnapshot(tooMany)
        !s5.getSnapshot(parent)
        s5.flatten() == [parent.path, "1:dir1", "1:dir"]

        def s6 = single.plus(snapshotDir(tooMany))
        s6.getSnapshot(dir1)
        s6.getSnapshot(child)
        s6.getSnapshot(tooMany)
        !s6.getSnapshot(dir2)
        !s6.getSnapshot(tooFew)
        !s6.getSnapshot(parent)
        s6.flatten() == [parent.path, "1:dir1", "1:dir12"]
    }

    def "can add dir to multi set"() {
        def parentParent = tmpDir.createDir()
        def parent = parentParent.createDir("parent")
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def other = parent.createDir("dir4")
        def child = dir1.createDir("child1")
        def multi = DefaultFileHierarchySet.of([snapshotDir(dir1), snapshotDir(dir2)])

        expect:
        def s1 = multi.plus(snapshotDir(dir3))
        s1.getSnapshot(dir1)
        s1.getSnapshot(child)
        s1.getSnapshot(dir2)
        s1.getSnapshot(dir3)
        !s1.getSnapshot(other)
        !s1.getSnapshot(parent)
        s1.flatten() == [parent.path, "1:dir1", "1:dir2", "1:dir3"]

        def s2 = multi.plus(snapshotDir(dir2))
        s2.getSnapshot(dir1)
        s2.getSnapshot(child)
        s2.getSnapshot(dir2)
        !s2.getSnapshot(dir3)
        !s2.getSnapshot(other)
        !s2.getSnapshot(parent)
        s2.flatten() == [parent.path, "1:dir1", "1:dir2"]

        def s3 = multi.plus(snapshotDir(child))
        // The parent directory snapshot was removed
        !s3.getSnapshot(dir1)
        s3.getSnapshot(child)
        s3.getSnapshot(dir2)
        !s3.getSnapshot(dir3)
        !s3.getSnapshot(other)
        !s3.getSnapshot(parent)
        s3.flatten() == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s4 = multi.plus(snapshotDir(parent))
        s4.getSnapshot(dir1)
        s4.getSnapshot(child)
        s4.getSnapshot(dir2)
        s4.getSnapshot(other)
        s4.getSnapshot(parent)
        s4.flatten() == [parent.path]
    }

    def "splits and merges prefixes as directories are added"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1dir2 = dir1.createDir("dir2")
        def dir1dir2dir3 = dir1dir2.createDir("dir3")
        def dir1dir2dir4 = dir1dir2.createDir("dir4")
        def dir1dir5 = dir1.createDir("dir5/and/more")
        def dir6 = parent.createDir("dir6")

        expect:
        def s1 = DefaultFileHierarchySet.of([dir1dir2dir3, dir1dir5].collect { snapshotDir(it) })
        s1.flatten() == [dir1.path, "1:dir2/dir3", "1:dir5/and/more"]

        def s2 = s1.plus(snapshotDir(dir1dir2dir4))
        s2.flatten() == [dir1.path, "1:dir2", "2:dir3", "2:dir4", "1:dir5/and/more"]

        def s3 = s2.plus(snapshotDir(dir6))
        s3.flatten() == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5/and/more", "1:dir6"]

        def s4 = s3.plus(snapshotDir(dir1dir2))
        s4.flatten() == [parent.path, "1:dir1", "2:dir2", "2:dir5/and/more", "1:dir6"]

        def s5 = s4.plus(snapshotDir(dir1))
        s5.flatten() == [parent.path, "1:dir1", "1:dir6"]

        def s6 = s3.plus(snapshotDir(dir1))
        s6.flatten() == [parent.path, "1:dir1", "1:dir6"]

        def s7 = s3.plus(snapshotDir(parent))
        s7.flatten() == [parent.path]
    }

    def "can add directory snapshot in between to forking points"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("sub/dir1")
        def dir2 = parent.createDir("sub/dir2")
        def dir3 = parent.createDir("dir3")

        when:
        def set = DefaultFileHierarchySet.of([dir1, dir2, dir3].collect { snapshotDir(it) })
        then:
        set.getSnapshot(dir1)
        set.getSnapshot(dir2)
        set.getSnapshot(dir3)
    }

    def "can add new parent"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("sub/dir1")
        def dir2 = parent.createDir("sub/dir2")

        when:
        def set = DefaultFileHierarchySet.of([dir1, dir2, parent].collect { snapshotDir(it) })
        then:
        set.getSnapshot(parent)
        set.getSnapshot(dir1)
        set.getSnapshot(dir2)
    }

    def "adding a snapshot in a known directory invalidates the directory"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def fileInDir = dir1.createFile("file1")
        def setWithDir1 = DefaultFileHierarchySet.of(snapshotDir(dir1))

        when:
        def subDir = dir1.file("sub").createDir()
        def set = setWithDir1.plus(snapshotDir(subDir))
        then:
        set.getSnapshot(subDir)
        set.getSnapshot(fileInDir)
        !set.getSnapshot(dir1)
    }

    private FileSystemLocationSnapshot snapshotDir(TestFile dir) {
        directorySnapshotter.snapshot(dir.absolutePath, null, new AtomicBoolean(false))
    }

    static HashCode hashFile(File file) {
        TestFiles.fileHasher().hash(file)
    }
    private static boolean equalSnapshots(FileSystemLocationSnapshot snapshot1, FileSystemLocationSnapshot snapshot2) {
        return snapshot1.absolutePath == snapshot2.absolutePath && snapshot1.hash == snapshot2.hash
    }

    private static void assertFileSnapshot(FileHierarchySet set, File location) {
        def snapshot = set.getSnapshot(location)
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.RegularFile
        assert snapshot.hash == hashFile(location)
    }

    private static void assertMissingFileSnapshot(FileHierarchySet set, File location) {
        def snapshot = set.getSnapshot(location)
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.Missing
    }
}
