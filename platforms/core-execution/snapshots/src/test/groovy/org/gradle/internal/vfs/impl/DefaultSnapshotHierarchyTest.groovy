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
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.FileType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.AbstractIncompleteFileSystemNode
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemNode
import org.gradle.internal.snapshot.MetadataSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.PathUtil
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotHierarchy
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification

import java.util.stream.Collectors

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultSnapshotHierarchyTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private static final SnapshotHierarchy EMPTY = DefaultSnapshotHierarchy.empty(CASE_SENSITIVE)

    DirectorySnapshotter directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner(), [], Stub(DirectorySnapshotterStatistics.Collector))

    def diffListener = new SnapshotHierarchy.NodeDiffListener() {
        @Override
        void nodeRemoved(FileSystemNode node) {
        }

        @Override
        void nodeAdded(FileSystemNode node) {
        }
    }

    def "creates from a single file"() {
        def dir = tmpDir.createDir("dir")
        def child = dir.file("child").createFile()
        expect:
        def set = snapshot(dir)
        assertDirectorySnapshot(set, dir)
        assertFileSnapshot(set, child)
        assertHasNoMetadata(set, dir.parentFile)
        assertHasNoMetadata(set, tmpDir.file("dir2"))
        assertHasNoMetadata(set, tmpDir.file("d"))
    }

    def "creates from multiple files"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1Child = dir1.file("child").createFile()
        def dir2 = parent.createDir("common/dir2")
        def dir2Child = dir2.file("child").createFile()
        def dir3 = parent.createDir("common/dir3")
        def dir3Child = dir3.file("child").createFile()

        expect:
        def set = snapshot(dir1, dir2, dir3)
        [dir1, dir2, dir3].each { File location ->
            assertDirectorySnapshot(set, location)
        }
        [dir1Child, dir2Child, dir3Child].each {
            assertFileSnapshot(set, it)
        }
        assertMissingFileSnapshot(set, dir2.file("some/non-existing/file"))
        assertPartialDirectoryNode(set, parent)
        assertPartialDirectoryNode(set, dir2.parentFile)
        assertHasNoMetadata(set, tmpDir.file("dir"))
        assertHasNoMetadata(set, tmpDir.file("dir12"))
        assertHasNoMetadata(set, tmpDir.file("common/dir21"))
        flatten(set) == [parent.path, "1:common", "2:dir2", "3:child", "2:dir3", "3:child", "1:dir1", "2:child"]
    }

    def "creates from files where one file is ancestor of the others"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = dir1.createDir("dir2")
        def dir1Child = dir1.file("child")
        dir1Child.createFile()
        def dir2Child = dir2.file("child/some/nested/structure").createFile()
        expect:
        def set = snapshot(dir2, dir1)
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
        assertFileSnapshot(set, dir1Child)
        assertFileSnapshot(set, dir2Child)
        assertHasNoMetadata(set, dir1.parentFile)
        assertHasNoMetadata(set, tmpDir.file("dir"))
        assertHasNoMetadata(set, tmpDir.file("dir12"))
        assertHasNoMetadata(set, tmpDir.file("dir21"))
        flatten(set) == [dir1.path, "1:child", "1:dir2", "2:child", "3:some", "4:nested", "5:structure"]
    }

    def "can add dir to empty set"() {
        def empty = EMPTY
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")

        def dir1Snapshot = snapshotDir(dir1)
        def dir2Snapshot = snapshotDir(dir2)
        expect:
        def s1 = empty.store(dir1.absolutePath, dir1Snapshot, diffListener)
        assertDirectorySnapshot(s1, dir1)
        assertHasNoMetadata(s1, dir2)

        def s2 = empty.store(dir2.absolutePath, dir2Snapshot, diffListener)
        assertHasNoMetadata(s2, dir1)
        s2.findMetadata(dir2.absolutePath).get() == dir2Snapshot
    }

    def "can add dir to singleton set"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def tooMany = parent.createDir("dir12")
        def tooFew = parent.createDir("dir")
        def child = dir1.createDir("child1")
        def single = snapshot(dir1)

        expect:
        def s1 = updateDir(single, dir2)
        assertDirectorySnapshot(s1, dir1)
        assertDirectorySnapshot(s1, child)
        assertDirectorySnapshot(s1, dir2)
        assertHasNoMetadata(s1, dir3)
        assertHasNoMetadata(s1, tooFew)
        assertHasNoMetadata(s1, tooMany)
        assertPartialDirectoryNode(s1, parent)
        flatten(s1) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s2 = updateDir(single, dir1)
        assertDirectorySnapshot(s2, dir1)
        assertDirectorySnapshot(s2, child)
        assertHasNoMetadata(s2, dir2)
        assertHasNoMetadata(s2, dir3)
        assertHasNoMetadata(s2, tooFew)
        assertHasNoMetadata(s2, tooMany)
        assertHasNoMetadata(s2, parent)
        flatten(s2) == [dir1.path, "1:child1"]

        def s3 = updateDir(single, child)
        assertDirectorySnapshot(s3, dir1)
        assertDirectorySnapshot(s3, child)
        assertHasNoMetadata(s3, dir2)
        assertHasNoMetadata(s3, dir3)
        assertHasNoMetadata(s3, tooFew)
        assertHasNoMetadata(s3, tooMany)
        assertHasNoMetadata(s3, parent)
        flatten(s3) == [dir1.path, "1:child1"]

        def s4 = updateDir(single, parent)
        assertDirectorySnapshot(s4, dir1)
        assertDirectorySnapshot(s4, child)
        assertDirectorySnapshot(s4, dir2)
        assertDirectorySnapshot(s4, dir3)
        assertDirectorySnapshot(s4, parent)
        flatten(s4) == [parent.path, "1:dir", "1:dir1", "2:child1", "1:dir12", "1:dir2", "1:dir3"]

        def s5 = updateDir(single, tooFew)
        assertDirectorySnapshot(s5, dir1)
        assertDirectorySnapshot(s5, child)
        assertDirectorySnapshot(s5, tooFew)
        assertHasNoMetadata(s5, dir2)
        assertHasNoMetadata(s5, tooMany)
        assertPartialDirectoryNode(s5, parent)
        flatten(s5) == [parent.path, "1:dir", "1:dir1", "2:child1"]

        def s6 = updateDir(single, tooMany)
        assertDirectorySnapshot(s6, dir1)
        assertDirectorySnapshot(s6, child)
        assertDirectorySnapshot(s6, tooMany)
        assertHasNoMetadata(s6, dir2)
        assertHasNoMetadata(s6, tooFew)
        assertPartialDirectoryNode(s6, parent)
        flatten(s6) == [parent.path, "1:dir1", "2:child1", "1:dir12"]
    }

    def "can add dir to multi set"() {
        def parentParent = tmpDir.createDir()
        def parent = parentParent.createDir("parent")
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def other = parent.createDir("dir4")
        def child = dir1.createDir("child1")
        def multi = snapshot(dir1, dir2)

        expect:
        def s1 = updateDir(multi, dir3)
        assertDirectorySnapshot(s1, dir1)
        assertDirectorySnapshot(s1, child)
        assertDirectorySnapshot(s1, dir2)
        assertDirectorySnapshot(s1, dir3)
        assertHasNoMetadata(s1, other)
        assertPartialDirectoryNode(s1, parent)
        flatten(s1) == [parent.path, "1:dir1", "2:child1", "1:dir2", "1:dir3"]

        def s2 = updateDir(multi, dir2)
        assertDirectorySnapshot(s2, dir1)
        assertDirectorySnapshot(s2, child)
        assertDirectorySnapshot(s2, dir2)
        assertHasNoMetadata(s2, dir3)
        assertHasNoMetadata(s2, other)
        assertPartialDirectoryNode(s2, parent)
        flatten(s2) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s3 = updateDir(multi, child)
        assertDirectorySnapshot(s3, dir1)
        assertDirectorySnapshot(s3, child)
        assertDirectorySnapshot(s3, dir2)
        assertHasNoMetadata(s3, dir3)
        assertHasNoMetadata(s3, other)
        assertPartialDirectoryNode(s2, parent)
        flatten(s3) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s4 = updateDir(multi, parent)
        assertDirectorySnapshot(s4, dir1)
        assertDirectorySnapshot(s4, child)
        assertDirectorySnapshot(s4, dir2)
        assertDirectorySnapshot(s4, other)
        assertPartialDirectoryNode(s2, parent)
        flatten(s4) == [parent.path, "1:dir1", "2:child1", "1:dir2", "1:dir3", "1:dir4"]
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
        def s1 = snapshot(dir1dir2dir3, dir1dir5)
        flatten(s1) == [dir1.path, "1:dir2/dir3", "1:dir5/and/more"]

        def s2 = updateDir(s1, dir1dir2dir4)
        flatten(s2) == [dir1.path, "1:dir2", "2:dir3", "2:dir4", "1:dir5/and/more"]

        def s3 = updateDir(s2, dir6)
        flatten(s3) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5/and/more", "1:dir6"]

        def s4 = updateDir(s3, dir1dir2)
        flatten(s4) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5/and/more", "1:dir6"]

        def s5 = updateDir(s4, dir1)
        flatten(s5) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5", "3:and", "4:more", "1:dir6"]

        def s6 = updateDir(s3, dir1)
        flatten(s6) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5", "3:and", "4:more", "1:dir6"]

        def s7 = updateDir(s3, parent)
        flatten(s7) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5", "3:and", "4:more", "1:dir6"]
    }

    def "visits the correct snapshots"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir1dir2 = dir1.createDir("dir2")
        def dir1dir2dir3 = dir1dir2.createDir("dir3")
        def dir1dir2dir4 = dir1dir2.createDir("dir4")
        def dir1dir5 = dir1.createDir("dir5/and/more")
        def dir6 = parent.createDir("dir6")

        expect:
        def s = snapshot(dir1dir2dir3, dir1dir5, dir1dir2dir4, dir6)
        flatten(s) == [parent.path, "1:dir1", "2:dir2", "3:dir3", "3:dir4", "2:dir5/and/more", "1:dir6"]
        s.hasDescendantsUnder(dir1.absolutePath)
        collectSnapshots(s, dir1.absolutePath)*.absolutePath == [dir1dir2dir3.absolutePath, dir1dir2dir4.absolutePath, dir1dir5.absolutePath]

        def pathWithPostfix = dir1dir2.absolutePath + "a"
        !s.hasDescendantsUnder(pathWithPostfix)
        collectSnapshots(s, pathWithPostfix).empty

        def sibling = new File(parent, "dir1/dir")
        !s.hasDescendantsUnder(sibling.absolutePath)
        collectSnapshots(s, sibling.absolutePath).empty

        def intermediate = dir1.file("dir5/and")
        s.hasDescendantsUnder(intermediate.absolutePath)
        collectSnapshots(s, intermediate.absolutePath)*.absolutePath == [dir1dir5.absolutePath]


        def anotherSibling = dir1.file("dir5/and/different")
        !s.hasDescendantsUnder(anotherSibling.absolutePath)
        collectSnapshots(s, anotherSibling.absolutePath).empty

        s.hasDescendantsUnder(dir1dir5.absolutePath)
        collectSnapshots(s, dir1dir5.absolutePath)*.absolutePath == [dir1dir5.absolutePath]
    }

    def "can add directory snapshot in between to forking points"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("sub/dir1")
        def dir2 = parent.createDir("sub/dir2")
        def dir3 = parent.createDir("dir3")

        when:
        def set = snapshot(dir1, dir2, dir3)
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
        assertDirectorySnapshot(set, dir3)
    }

    def "can update existing snapshots"() {
        def dir = tmpDir.createDir("dir")
        def child = dir.createFile("child")
        def set = snapshot(dir)

        when:
        child.text = "updated"
        set = updateDir(invalidate(set, child), dir)
        then:
        assertDirectorySnapshot(set, dir)
        assertFileSnapshot(set, child)
    }

    def "can update file snapshot with sub-dir snapshot"() {
        def dir = tmpDir.createFile("dir")
        def child = dir.file("sub/child")
        def set = snapshot(dir)

        when:
        dir.delete()
        child.text = "created"
        set = updateDir(invalidate(set, child), dir.file("sub"))
        then:
        assertDirectorySnapshot(set, dir.file("sub"))
        assertFileSnapshot(set, child)
    }

    def "can add new parent"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("sub/dir1")
        def dir2 = parent.createDir("sub/dir2")

        when:
        def set = snapshot(dir1, dir2, parent)
        then:
        assertDirectorySnapshot(set, parent)
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
    }

    def "adding a snapshot in a known directory is ignored"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def fileInDir = dir1.createFile("file1")
        def setWithDir1 = snapshot(dir1)

        when:
        def subDir = dir1.file("sub").createDir()
        def set = updateDir(setWithDir1, subDir)
        then:
        assertMissingFileSnapshot(set, subDir)
        assertFileSnapshot(set, fileInDir)
        set.findSnapshot(dir1.absolutePath).get() instanceof DirectorySnapshot
    }

    def "returns missing snapshots for children of files"() {
        def existing = tmpDir.createFile("existing")
        def missing = tmpDir.file("missing")

        when:
        def set = snapshot(existing, missing)
        then:
        assertFileSnapshot(set, existing)
        assertMissingFileSnapshot(set, missing)
        assertMissingFileSnapshot(set, existing.file("some/sub/path"))
        assertMissingFileSnapshot(set, missing.file("some/sub/path"))
    }

    def "can invalidate paths"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("sub/more/dir2")
        def dir2File = dir2.file("existing").createFile()
        def dir2FileSibling = dir2.file("sibling").createFile()
        def dir3 = parent.createDir("sub/more/dir3")
        def fullSet = snapshot(dir1, dir2, dir3)

        when:
        def set = invalidate(fullSet, dir2)
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir3)
        assertHasNoMetadata(set, dir2)

        when:
        set = invalidate(fullSet, dir2.parentFile)
        then:
        assertDirectorySnapshot(set, dir1)
        assertHasNoMetadata(set, dir2)
        assertHasNoMetadata(set, dir3)

        when:
        set = invalidate(fullSet, dir2.file("non-exisiting"))
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir3)
        assertFileSnapshot(set, dir2File)
        assertFileSnapshot(set, dir2FileSibling)
        assertPartialDirectoryNode(set, dir2)

        when:
        set = invalidate(fullSet, dir2File)
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir3)
        assertFileSnapshot(set, dir2FileSibling)
        assertPartialDirectoryNode(set, dir2)
        assertHasNoMetadata(set, dir2File)

        when:
        set = invalidate(fullSet, parent.file("sub/more/dir4"))
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
        assertDirectorySnapshot(set, dir3)
        assertHasNoMetadata(set, parent.file("sub/more/dir4"))

        when:
        set = invalidate(fullSet, parent.file("sub/else"))
        then:
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
        assertDirectorySnapshot(set, dir3)
        assertHasNoMetadata(set, parent.file("sub/else"))
    }

    def "can invalidate child of file"() {
        def file = tmpDir.createFile("some/dir/file.txt")
        def set = snapshot(file)

        when:
        set = invalidate(set, file.file("child"))
        then:
        assertHasNoMetadata(set, file)
    }

    def "can invalidate branching off of snapshot"() {
        def dir = tmpDir.createDir("some/sub/dir")
        def invalidatedLocation = tmpDir.file("some/other/file")
        def set = snapshot(dir)

        when:
        def invalidatedSet = invalidate(set, invalidatedLocation)
        then:
        assertDirectorySnapshot(invalidatedSet, dir)
        assertHasNoMetadata(invalidatedSet, invalidatedLocation)
        invalidatedSet.is(set)
    }

    def "root is handled correctly"() {
        Assume.assumeTrue("Root is only defined for the file separator '/'", File.separator == '/')

        when:
        def set = EMPTY.store("/", new DirectorySnapshot("/", "", AccessType.DIRECT, TestHashCodes.hashCodeFrom(1111), [new RegularFileSnapshot("/root.txt", "root.txt", TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(1, 1, AccessType.DIRECT))]), diffListener)
        then:
        set.findMetadata("/root.txt").get().type == FileType.RegularFile
        set.hasDescendantsUnder("/root.txt")
        collectSnapshots(set, "/root.txt")[0].type == FileType.RegularFile
        set.hasDescendantsUnder("/")
        collectSnapshots(set, "/")[0].type == FileType.Directory

        when:
        set = set.invalidate("/root.txt", diffListener).store("/", new DirectorySnapshot("/", "", AccessType.DIRECT, TestHashCodes.hashCodeFrom(2222), [new RegularFileSnapshot("/base.txt", "base.txt", TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(1, 1, AccessType.DIRECT))]), diffListener)
        then:
        set.findMetadata("/base.txt").get().type == FileType.RegularFile
    }

    static Collection<FileSystemLocationSnapshot> collectSnapshots(SnapshotHierarchy set, String path) {
        return set.rootSnapshotsUnder(path)
            .collect(Collectors::toList()) as Collection<FileSystemLocationSnapshot>
    }

    def "updates are inserted sorted"() {
        def parent = tmpDir.createDir()
        def childA = parent.file("a")
        def childB = parent.file("b")
        def childB1 = parent.file("b1")

        when:
        def set = snapshot(childA, childB, childB1)
        then:
        flatten(set) == [parent.absolutePath, "1:a", "1:b", "1:b1"]

        when:
        set = snapshot(parent.file("a/b/c"), parent.file("a/b-c/c"), parent.file("a/b/d"))
        then:
        flatten(set) == [childA.absolutePath, "1:b", "2:c", "2:d", "1:b-c/c"]

        when:
        set = snapshot(parent.file("a/b/c/a"), parent.file("a/b/c/b"), parent.file("a/b-c/c"), parent.file("a/b/d"))
        then:
        flatten(set) == [childA.absolutePath, "1:b", "2:c", "3:a", "3:b", "2:d", "1:b-c/c"]
    }

    def "can add to completely different paths with Unix paths"() {
        def firstPath = "/var/log"
        def secondPath = "/usr/bin"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath), diffListener)
            .store(secondPath, directorySnapshotForPath(secondPath), diffListener)
            .store("/other/just-checking", directorySnapshotForPath("/other/just-checking"), diffListener)

        expect:
        set.findSnapshot(firstPath).present
        set.findSnapshot(secondPath).present
        set.findMetadata("/").get().type == FileType.Directory
        !set.findSnapshot("/").present

        when:
        def invalidated = set.invalidate(firstPath, diffListener)
        then:
        !invalidated.findMetadata(firstPath).present
        invalidated.findMetadata(secondPath).present
        set.findMetadata("/").get().type == FileType.Directory
        !set.findSnapshot("/").present
    }

    def "can update the root path"() {
        when:
        def set = EMPTY
            .store("/", rootDirectorySnapshot(), diffListener)
        then:
        set.findMetadata("/").present
        set.findMetadata("/root.txt").get().type == FileType.RegularFile
        assertMissingFileSnapshot(set, new File("some/other/path"))

        when:
        set = EMPTY
            .store("/some/path", directorySnapshotForPath("/some/path"), diffListener)
            .store("/", rootDirectorySnapshot(), diffListener)
        then:
        set.findMetadata("/").present
        set.findMetadata("/root.txt").get().type == FileType.RegularFile
        assertMissingFileSnapshot(set, new File("some/path"))

        when:
        set = EMPTY
            .store("/firstPath", directorySnapshotForPath("/firstPath"), diffListener)
            .store("/secondPath", directorySnapshotForPath("/secondPath"), diffListener)
        then:
        set.invalidate("/", diffListener) == EMPTY

        when:
        set = EMPTY
            .store("/", rootDirectorySnapshot(), diffListener)
            .invalidate("/root.txt", diffListener)
        then:
        set.findMetadata("/").get().type == FileType.Directory
        !set.findSnapshot("/").present
        !set.findMetadata("/root.txt").present
        set.findMetadata("/other.txt").get().type == FileType.RegularFile
    }

    def "can add to completely different paths with Windows paths"() {
        def firstPath = "C:\\Windows\\log"
        def secondPath = "D:\\Users\\bin"
        def thirdPath = "E:\\Some\\other"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath), diffListener)
            .store(secondPath, directorySnapshotForPath(secondPath), diffListener)
            .store(thirdPath, directorySnapshotForPath(thirdPath), diffListener)

        expect:
        set.findMetadata(firstPath).present
        set.findMetadata(secondPath).present
        set.findMetadata(thirdPath).present

        when:
        def invalidated = set.invalidate(firstPath, diffListener)
        then:
        !invalidated.findMetadata(firstPath).present
        invalidated.findMetadata(secondPath).present

        when:
        invalidated = set.invalidate("C:\\", diffListener)
        then:
        !invalidated.findMetadata(firstPath).present
        invalidated.findMetadata(secondPath).present

        when:
        invalidated = set.invalidate("D:\\", diffListener)
        then:
        invalidated.findMetadata(firstPath).present
        !invalidated.findMetadata(secondPath).present
    }

    def "can handle UNC paths"() {
        def firstPath = "\\\\server\\some"
        def secondPath = "\\\\server\\whatever"
        def thirdPath = "\\\\otherServer\\whatever"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath), diffListener)
            .store(secondPath, directorySnapshotForPath(secondPath), diffListener)
            .store(thirdPath, directorySnapshotForPath(thirdPath), diffListener)
            .store("C:\\Some\\Location", directorySnapshotForPath("C:\\Some\\Location"), diffListener)

        expect:
        set.findMetadata(firstPath).present
        set.findMetadata(secondPath).present
        set.findMetadata(thirdPath).present

        when:
        def invalidated = set.invalidate(firstPath, diffListener)
        then:
        !invalidated.findMetadata(firstPath).present
        invalidated.findMetadata(secondPath).present

        when:
        invalidated = set.invalidate("\\\\server", diffListener)
        then:
        !invalidated.findMetadata(firstPath).present
        !invalidated.findMetadata(secondPath).present
        invalidated.findMetadata(thirdPath).present

        when:
        invalidated = set.invalidate("\\\\otherServer", diffListener)
        then:
        invalidated.findMetadata(firstPath).present
        invalidated.findMetadata(secondPath).present
        !invalidated.findMetadata(thirdPath).present
    }

    def "findSnapshot returns root node when queried at the root"() {
        def rootNode = Mock(FileSystemNode)
        def hierarchy = DefaultSnapshotHierarchy.from(rootNode, CASE_SENSITIVE)

        when:
        def foundSnapshot = hierarchy.findMetadata("/")
        then:
        foundSnapshot.present
        1 * rootNode.snapshot >> Optional.of(Mock(MetadataSnapshot))
        0 * _
    }

    def "hasDescendants can query the root"() {
        def rootNode = Mock(FileSystemNode)
        def hierarchy = DefaultSnapshotHierarchy.from(rootNode, CASE_SENSITIVE)

        when:
        def hasDescendants = hierarchy.hasDescendantsUnder("/")
        then:
        hasDescendants
        1 * rootNode.hasDescendants() >> true
        0 * _
    }

    def 'rootSnapshotsUnder can stream the root'() {
        def rootNode = Mock(FileSystemNode)
        def hierarchy = DefaultSnapshotHierarchy.from(rootNode, CASE_SENSITIVE)

        when:
        hierarchy.rootSnapshotsUnder("/")
        then:
        1 * rootNode.rootSnapshots()
        0 * _
    }

    def "store overwrites root node when storing at the root"() {
        def rootNode = Mock(FileSystemNode)
        def newRoot = Mock(FileSystemNode)
        def snapshot = Mock(MetadataSnapshot)
        def hierarchy = DefaultSnapshotHierarchy.from(rootNode, CASE_SENSITIVE)

        when:
        def newHierarchy = hierarchy.store("/", snapshot, SnapshotHierarchy.NodeDiffListener.NOOP)
        then:
        1 * snapshot.asFileSystemNode() >> newRoot
        0 * _

        when:
        def foundSnapshot = newHierarchy.findMetadata("/")
        then:
        foundSnapshot.get() is snapshot
        1 * newRoot.snapshot >> Optional.of(snapshot)
        0 * _
    }

    def "can invalidate the root"() {
        def rootNode = Mock(FileSystemNode)
        def hierarchy = DefaultSnapshotHierarchy.from(rootNode, CASE_SENSITIVE)

        when:
        def newHierarchy = hierarchy.invalidate("/", SnapshotHierarchy.NodeDiffListener.NOOP)
        then:
        0 * _

        when:
        def rootMetadata = newHierarchy.findMetadata("/")
        then:
        !rootMetadata.present
        0 * _
    }

    private static DirectorySnapshot rootDirectorySnapshot() {
        new DirectorySnapshot("/", "", AccessType.DIRECT, TestHashCodes.hashCodeFrom(1111), [
            new RegularFileSnapshot("/root.txt", "root.txt", TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(1, 1, AccessType.DIRECT)),
            new RegularFileSnapshot("/other.txt", "other.txt", TestHashCodes.hashCodeFrom(4321), DefaultFileMetadata.file(5, 28, AccessType.DIRECT))
        ])
    }

    private static DirectorySnapshot directorySnapshotForPath(String absolutePath) {
        new DirectorySnapshot(absolutePath, PathUtil.getFileName(absolutePath), AccessType.DIRECT, TestHashCodes.hashCodeFrom(1111), [
            new RegularFileSnapshot("${absolutePath}/root.txt", "root.txt", TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(1, 1, AccessType.DIRECT))
        ])
    }

    private FileSystemLocationSnapshot snapshotDir(File dir) {
        directorySnapshotter.snapshot(dir.absolutePath, null, [:]) {}
    }

    private static FileSystemLocationSnapshot snapshotFile(File file) {
        if (!file.exists()) {
            return new MissingFileSnapshot(file.absolutePath, file.name, AccessType.DIRECT)
        }
        return new RegularFileSnapshot(file.absolutePath, file.name, TestFiles.fileHasher().hash(file), TestFiles.fileSystem().stat(file))
    }

    static HashCode hashFile(File file) {
        TestFiles.fileHasher().hash(file)
    }

    private static void assertFileSnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.findMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.RegularFile
        assert snapshot.hash == hashFile(location)
    }

    private void assertDirectorySnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.findMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.Directory
        assert snapshot.hash == snapshotDir(location).hash
    }

    private static void assertPartialDirectoryNode(SnapshotHierarchy set, File location) {
        def snapshot = set.findMetadata(location.absolutePath).get()
        assert snapshot.type == FileType.Directory
        assert !(snapshot instanceof FileSystemLocationSnapshot)
    }

    private static void assertMissingFileSnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.findMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.Missing
    }

    private static void assertHasNoMetadata(SnapshotHierarchy set, File location) {
        assert !set.findMetadata(location.absolutePath).present
    }


    private SnapshotHierarchy invalidate(SnapshotHierarchy set, File location) {
        set.invalidate(location.absolutePath, diffListener)
    }

    private SnapshotHierarchy snapshot(File... locations) {
        SnapshotHierarchy set = EMPTY
        for (File location : locations) {
            set = set.store(location.absolutePath, location.directory ? snapshotDir(location) : snapshotFile(location), diffListener)
        }
        return set
    }

    private SnapshotHierarchy updateDir(SnapshotHierarchy set, File dir) {
        set.store(dir.absolutePath, snapshotDir(dir), diffListener)
    }

    private static List<String> flatten(SnapshotHierarchy set) {
        if (!(set instanceof DefaultSnapshotHierarchy)) {
            return []
        }
        List<String> prefixes = new ArrayList<>()
        def node = set.rootNode
        def unpackedNode = (node.getSnapshot()
            .filter { it instanceof FileSystemLocationSnapshot }
            .orElse(node))
        if (unpackedNode instanceof DirectorySnapshot) {
            def children = unpackedNode.children
            children.forEach { child ->
                collectPrefixes(child.name, child, 0, prefixes)
            }
        } else if (unpackedNode instanceof AbstractIncompleteFileSystemNode) {
            def children = unpackedNode.children
            children.stream()
                .forEach(child -> collectPrefixes(child.path, child.value, 0, prefixes))
        }
        return prefixes
    }

    private static void collectPrefixes(String path, FileSystemNode node, int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add((File.separator == '/' ? '/' : "") + path)
        } else {
            prefixes.add(depth + ":" + path.replace(File.separatorChar, (char) '/'))
        }
        def unpackedNode = (node.getSnapshot()
            .filter { it instanceof FileSystemLocationSnapshot }
            .orElse(node))
        if (unpackedNode instanceof DirectorySnapshot) {
            def children = unpackedNode.children
            children.forEach { child ->
                collectPrefixes(child.name, child, depth + 1, prefixes)
            }
        } else if (unpackedNode instanceof AbstractIncompleteFileSystemNode) {
            def children = unpackedNode.children
            children.stream()
                .forEach(child -> collectPrefixes(child.path, child.value, depth + 1, prefixes))
        }
    }
}
