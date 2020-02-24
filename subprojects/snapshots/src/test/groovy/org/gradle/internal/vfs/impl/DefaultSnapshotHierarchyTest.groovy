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
import org.gradle.internal.snapshot.AbstractIncompleteSnapshotWithChildren
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.FileSystemNode
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.PathUtil
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.internal.vfs.SnapshotHierarchy
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class DefaultSnapshotHierarchyTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    private static final SnapshotHierarchy EMPTY = DefaultSnapshotHierarchy.empty(CASE_SENSITIVE)

    DirectorySnapshotter directorySnapshotter = new DirectorySnapshotter(TestFiles.fileHasher(), new StringInterner())

    def "creates from a single file"() {
        def dir = tmpDir.createDir("dir")
        def child = dir.file("child").createFile()
        expect:
        def set = fileHierarchySet(dir)
        assertDirectorySnapshot(set, dir)
        assertFileSnapshot(set, child)
        !snapshotPresent(set, dir.parentFile)
        !snapshotPresent(set, tmpDir.file("dir2"))
        !snapshotPresent(set, tmpDir.file("d"))
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
        def set = fileHierarchySet([dir1, dir2, dir3])
        [dir1, dir2, dir3].each { File location ->
            assertDirectorySnapshot(set, location)
        }
        [dir1Child, dir2Child, dir3Child].each {
            assertFileSnapshot(set, it)
        }
        assertMissingFileSnapshot(set, dir2.file("some/non-existing/file"))
        assertPartialDirectorySnapshot(set, parent)
        assertPartialDirectorySnapshot(set, dir2.parentFile)
        !snapshotPresent(set, tmpDir.file("dir"))
        !snapshotPresent(set, tmpDir.file("dir12"))
        !snapshotPresent(set, tmpDir.file("common/dir21"))
        flatten(set) == [parent.path, "1:common", "2:dir2", "3:child", "2:dir3", "3:child", "1:dir1", "2:child"]
    }

    def "creates from files where one file is ancestor of the others"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = dir1.createDir("dir2")
        def dir1Child = dir1.file("child")
        dir1Child.createFile()
        def dir2Child = dir2.file("child/some/nested/structure").createFile()
        expect:
        def set = fileHierarchySet([dir2, dir1])
        assertDirectorySnapshot(set, dir1)
        assertDirectorySnapshot(set, dir2)
        assertFileSnapshot(set, dir1Child)
        assertFileSnapshot(set, dir2Child)
        !snapshotPresent(set, dir1.parentFile)
        !snapshotPresent(set, tmpDir.file("dir"))
        !snapshotPresent(set, tmpDir.file("dir12"))
        !snapshotPresent(set, tmpDir.file("dir21"))
        flatten(set) == [dir1.path, "1:child", "1:dir2", "2:child", "3:some", "4:nested", "5:structure"]
    }

    def "can add dir to empty set"() {
        def empty = EMPTY
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")

        def dir1Snapshot = snapshotDir(dir1)
        def dir2Snapshot = snapshotDir(dir2)
        expect:
        def s1 = empty.store(dir1.absolutePath, dir1Snapshot)
        snapshotPresent(s1, dir1)
        !snapshotPresent(s1, dir2)

        def s2 = empty.store(dir2.absolutePath, dir2Snapshot)
        !snapshotPresent(s2, dir1)
        s2.getMetadata(dir2.absolutePath).get() == dir2Snapshot
    }

    def "can add dir to singleton set"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def dir2 = parent.createDir("dir2")
        def dir3 = parent.createDir("dir3")
        def tooMany = parent.createDir("dir12")
        def tooFew = parent.createDir("dir")
        def child = dir1.createDir("child1")
        def single = fileHierarchySet(dir1)

        expect:
        def s1 = updateDir(single, dir2)
        assertDirectorySnapshot(s1, dir1)
        assertDirectorySnapshot(s1, child)
        assertDirectorySnapshot(s1, dir2)
        !snapshotPresent(s1, dir3)
        !snapshotPresent(s1, tooFew)
        !snapshotPresent(s1, tooMany)
        assertPartialDirectorySnapshot(s1, parent)
        flatten(s1) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s2 = updateDir(single, dir1)
        assertDirectorySnapshot(s2, dir1)
        assertDirectorySnapshot(s2, child)
        !snapshotPresent(s2, dir2)
        !snapshotPresent(s2, dir3)
        !snapshotPresent(s2, tooFew)
        !snapshotPresent(s2, tooMany)
        !snapshotPresent(s2, parent)
        flatten(s2) == [dir1.path, "1:child1"]

        def s3 = updateDir(single, child)
        assertDirectorySnapshot(s3, dir1)
        assertDirectorySnapshot(s3, child)
        !snapshotPresent(s3, dir2)
        !snapshotPresent(s3, dir3)
        !snapshotPresent(s3, tooFew)
        !snapshotPresent(s3, tooMany)
        !snapshotPresent(s3, parent)
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
        !snapshotPresent(s5, dir2)
        !snapshotPresent(s5, tooMany)
        assertPartialDirectorySnapshot(s5, parent)
        flatten(s5) == [parent.path, "1:dir", "1:dir1", "2:child1"]

        def s6 = updateDir(single, tooMany)
        assertDirectorySnapshot(s6, dir1)
        assertDirectorySnapshot(s6, child)
        assertDirectorySnapshot(s6, tooMany)
        !snapshotPresent(s6, dir2)
        !snapshotPresent(s6, tooFew)
        assertPartialDirectorySnapshot(s6, parent)
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
        def multi = fileHierarchySet([dir1, dir2])

        expect:
        def s1 = updateDir(multi, dir3)
        snapshotPresent(s1, dir1)
        snapshotPresent(s1, child)
        snapshotPresent(s1, dir2)
        snapshotPresent(s1, dir3)
        !snapshotPresent(s1, other)
        assertPartialDirectorySnapshot(s1, parent)
        flatten(s1) == [parent.path, "1:dir1", "2:child1", "1:dir2", "1:dir3"]

        def s2 = updateDir(multi, dir2)
        snapshotPresent(s2, dir1)
        snapshotPresent(s2, child)
        snapshotPresent(s2, dir2)
        !snapshotPresent(s2, dir3)
        !snapshotPresent(s2, other)
        assertPartialDirectorySnapshot(s2, parent)
        flatten(s2) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s3 = updateDir(multi, child)
        snapshotPresent(s3, dir1)
        snapshotPresent(s3, child)
        snapshotPresent(s3, dir2)
        !snapshotPresent(s3, dir3)
        !snapshotPresent(s3, other)
        assertPartialDirectorySnapshot(s2, parent)
        flatten(s3) == [parent.path, "1:dir1", "2:child1", "1:dir2"]

        def s4 = updateDir(multi, parent)
        snapshotPresent(s4, dir1)
        snapshotPresent(s4, child)
        snapshotPresent(s4, dir2)
        snapshotPresent(s4, other)
        assertPartialDirectorySnapshot(s2, parent)
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
        def s1 = fileHierarchySet([dir1dir2dir3, dir1dir5])
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

    def "can add directory snapshot in between to forking points"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("sub/dir1")
        def dir2 = parent.createDir("sub/dir2")
        def dir3 = parent.createDir("dir3")

        when:
        def set = fileHierarchySet([dir1, dir2, dir3])
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir2)
        snapshotPresent(set, dir3)
    }

    def "can update existing snapshots"() {
        def dir = tmpDir.createDir("dir")
        def child = dir.createFile("child")
        def set = fileHierarchySet(dir)

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
        def set = fileHierarchySet(dir)

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
        def set = fileHierarchySet([dir1, dir2, parent])
        then:
        snapshotPresent(set, parent)
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir2)
    }

    def "adding a snapshot in a known directory is ignored"() {
        def parent = tmpDir.createDir()
        def dir1 = parent.createDir("dir1")
        def fileInDir = dir1.createFile("file1")
        def setWithDir1 = fileHierarchySet(dir1)

        when:
        def subDir = dir1.file("sub").createDir()
        def set = updateDir(setWithDir1, subDir)
        then:
        snapshotPresent(set, subDir)
        snapshotPresent(set, fileInDir)
        snapshotPresent(set, dir1)
    }

    def "returns missing snapshots for children of files"() {
        def existing = tmpDir.createFile("existing")
        def missing = tmpDir.file("missing")

        when:
        def set = fileHierarchySet([existing, missing])
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
        def fullSet = fileHierarchySet([dir1, dir2, dir3])

        when:
        def set = invalidate(fullSet, dir2)
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir3)
        !snapshotPresent(set, dir2)

        when:
        set = invalidate(fullSet, dir2.parentFile)
        then:
        snapshotPresent(set, dir1)
        !snapshotPresent(set, dir2)
        !snapshotPresent(set, dir3)

        when:
        set = invalidate(fullSet, dir2.file("non-exisiting"))
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir3)
        snapshotPresent(set, dir2File)
        snapshotPresent(set, dir2FileSibling)
        assertPartialDirectorySnapshot(set, dir2)

        when:
        set = invalidate(fullSet, dir2File)
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir3)
        snapshotPresent(set, dir2FileSibling)
        assertPartialDirectorySnapshot(set, dir2)
        !snapshotPresent(set, dir2File)

        when:
        set = invalidate(fullSet, parent.file("sub/more/dir4"))
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir2)
        snapshotPresent(set, dir3)
        !snapshotPresent(set, parent.file("sub/more/dir4"))

        when:
        set = invalidate(fullSet, parent.file("sub/else"))
        then:
        snapshotPresent(set, dir1)
        snapshotPresent(set, dir2)
        snapshotPresent(set, dir3)
        !snapshotPresent(set, parent.file("sub/else"))
    }

    def "can invalidate child of file"() {
        def file = tmpDir.createFile("some/dir/file.txt")
        def set = fileHierarchySet(file)

        when:
        set = invalidate(set, file.file("child"))
        then:
        !snapshotPresent(set, file)
    }

    def "can invalidate branching off of snapshot"() {
        def file = tmpDir.createDir("some/sub/dir")
        def invalidatedLocation = tmpDir.file("some/other/file")
        def set = fileHierarchySet(file)

        when:
        set = invalidate(set, invalidatedLocation)
        then:
        snapshotPresent(set, file)
        !snapshotPresent(set, invalidatedLocation)
    }

    def "root is handled correctly"() {
        Assume.assumeTrue("Root is only defined for the file separator '/'", File.separator == '/')

        when:
        def set = EMPTY.store("/", new CompleteDirectorySnapshot("/", "", [new RegularFileSnapshot("/root.txt", "root.txt", HashCode.fromInt(1234), new FileMetadata(1, 1))], HashCode.fromInt(1111)))
        then:
        set.getMetadata("/root.txt").get().type == FileType.RegularFile

        when:
        set = set.invalidate("/root.txt").store("/", new CompleteDirectorySnapshot("/", "", [new RegularFileSnapshot("/base.txt", "base.txt", HashCode.fromInt(1234), new FileMetadata(1, 1))], HashCode.fromInt(2222)))
        then:
        set.getMetadata("/base.txt").get().type == FileType.RegularFile
    }

    def "updates are inserted sorted"() {
        def parent = tmpDir.createDir()
        def childA = parent.file("a")
        def childB = parent.file("b")
        def childB1 = parent.file("b1")

        when:
        def set = fileHierarchySet([childA, childB, childB1])
        then:
        flatten(set) == [parent.absolutePath, "1:a", "1:b", "1:b1"]

        when:
        set = fileHierarchySet([parent.file("a/b/c"), parent.file("a/b-c/c"), parent.file("a/b/d")])
        then:
        flatten(set) == [childA.absolutePath, "1:b", "2:c", "2:d", "1:b-c/c"]

        when:
        set = fileHierarchySet([parent.file("a/b/c/a"), parent.file("a/b/c/b"), parent.file("a/b-c/c"), parent.file("a/b/d")])
        then:
        flatten(set) == [childA.absolutePath, "1:b", "2:c", "3:a", "3:b", "2:d", "1:b-c/c"]
    }

    def "can add to completely different paths with Unix paths"() {
        def firstPath = "/var/log"
        def secondPath = "/usr/bin"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath))
            .store(secondPath, directorySnapshotForPath(secondPath))
            .store("/other/just-checking", directorySnapshotForPath("/other/just-checking"))

        expect:
        set.getSnapshot(firstPath).present
        set.getSnapshot(secondPath).present
        set.getMetadata("/").get().type == FileType.Directory
        !set.getSnapshot("/").present

        when:
        def invalidated = set.invalidate(firstPath)
        then:
        !invalidated.getMetadata(firstPath).present
        invalidated.getMetadata(secondPath).present
        set.getMetadata("/").get().type == FileType.Directory
        !set.getSnapshot("/").present
    }

    def "can update the root path"() {
        when:
        def set = EMPTY
            .store("/", rootDirectorySnapshot())
        then:
        set.getMetadata("/").present
        set.getMetadata("/root.txt").get().type == FileType.RegularFile
        assertMissingFileSnapshot(set, new File("some/other/path"))

        when:
        set = EMPTY
            .store("/some/path", directorySnapshotForPath("/some/path"))
            .store("/", rootDirectorySnapshot())
        then:
        set.getMetadata("/").present
        set.getMetadata("/root.txt").get().type == FileType.RegularFile
        assertMissingFileSnapshot(set, new File("some/path"))

        when:
        set = EMPTY
            .store("/firstPath", directorySnapshotForPath("/firstPath"))
            .store("/secondPath", directorySnapshotForPath("/secondPath"))
        then:
        set.invalidate("/") == EMPTY

        when:
        set = EMPTY
            .store("/", rootDirectorySnapshot())
            .invalidate("/root.txt")
        then:
        set.getMetadata("/").get().type == FileType.Directory
        !set.getSnapshot("/").present
        !set.getMetadata("/root.txt").present
        set.getMetadata("/other.txt").get().type == FileType.RegularFile
    }

    def "can add to completely different paths with Windows paths"() {
        def firstPath = "C:\\Windows\\log"
        def secondPath = "D:\\Users\\bin"
        def thirdPath = "E:\\Some\\other"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath))
            .store(secondPath, directorySnapshotForPath(secondPath))
            .store(thirdPath, directorySnapshotForPath(thirdPath))

        expect:
        set.getMetadata(firstPath).present
        set.getMetadata(secondPath).present
        set.getMetadata(thirdPath).present

        when:
        def invalidated = set.invalidate(firstPath)
        then:
        !invalidated.getMetadata(firstPath).present
        invalidated.getMetadata(secondPath).present

        when:
        invalidated = set.invalidate("C:\\")
        then:
        !invalidated.getMetadata(firstPath).present
        invalidated.getMetadata(secondPath).present

        when:
        invalidated = set.invalidate("D:\\")
        then:
        invalidated.getMetadata(firstPath).present
        !invalidated.getMetadata(secondPath).present
    }

    def "can handle UNC paths"() {
        def firstPath = "\\\\server\\some"
        def secondPath = "\\\\server\\whatever"
        def thirdPath = "\\\\otherServer\\whatever"

        def set = EMPTY
            .store(firstPath, directorySnapshotForPath(firstPath))
            .store(secondPath, directorySnapshotForPath(secondPath))
            .store(thirdPath, directorySnapshotForPath(thirdPath))
            .store("C:\\Some\\Location", directorySnapshotForPath("C:\\Some\\Location"))

        expect:
        set.getMetadata(firstPath).present
        set.getMetadata(secondPath).present
        set.getMetadata(thirdPath).present

        when:
        def invalidated = set.invalidate(firstPath)
        then:
        !invalidated.getMetadata(firstPath).present
        invalidated.getMetadata(secondPath).present

        when:
        invalidated = set.invalidate("\\\\server")
        then:
        !invalidated.getMetadata(firstPath).present
        !invalidated.getMetadata(secondPath).present
        invalidated.getMetadata(thirdPath).present

        when:
        invalidated = set.invalidate("\\\\otherServer")
        then:
        invalidated.getMetadata(firstPath).present
        invalidated.getMetadata(secondPath).present
        !invalidated.getMetadata(thirdPath).present
    }

    private static CompleteDirectorySnapshot rootDirectorySnapshot() {
        new CompleteDirectorySnapshot("/", "", [
            new RegularFileSnapshot("/root.txt", "root.txt", HashCode.fromInt(1234), new FileMetadata(1, 1)),
            new RegularFileSnapshot("/other.txt", "other.txt", HashCode.fromInt(4321), new FileMetadata(5, 28))
        ], HashCode.fromInt(1111))
    }

    private static CompleteDirectorySnapshot directorySnapshotForPath(String absolutePath) {
        new CompleteDirectorySnapshot(absolutePath, PathUtil.getFileName(absolutePath), [new RegularFileSnapshot("${absolutePath}/root.txt", "root.txt", HashCode.fromInt(1234), new FileMetadata(1, 1))], HashCode.fromInt(1111))
    }

    private CompleteFileSystemLocationSnapshot snapshotDir(File dir) {
        directorySnapshotter.snapshot(dir.absolutePath, null, new AtomicBoolean(false))
    }

    private static CompleteFileSystemLocationSnapshot snapshotFile(File file) {
        if (!file.exists()) {
            return new MissingFileSnapshot(file.absolutePath, file.name)
        }
        return new RegularFileSnapshot(file.absolutePath, file.name, TestFiles.fileHasher().hash(file), FileMetadata.from(TestFiles.fileSystem().stat(file)))
    }

    static HashCode hashFile(File file) {
        TestFiles.fileHasher().hash(file)
    }
    private static void assertFileSnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.getMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.RegularFile
        assert snapshot.hash == hashFile(location)
    }

    private void assertDirectorySnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.getMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.Directory
        assert snapshot.hash == snapshotDir(location).hash

    }

    private static void assertPartialDirectorySnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.getMetadata(location.absolutePath).get()
        assert snapshot.type == FileType.Directory
        assert !(snapshot instanceof CompleteFileSystemLocationSnapshot)
    }

    private static void assertMissingFileSnapshot(SnapshotHierarchy set, File location) {
        def snapshot = set.getMetadata(location.absolutePath).get()
        assert snapshot.absolutePath == location.absolutePath
        assert snapshot.name == location.name
        assert snapshot.type == FileType.Missing
    }

    private static boolean snapshotPresent(SnapshotHierarchy set, File location) {
        set.getMetadata(location.absolutePath).present
    }

    private static SnapshotHierarchy invalidate(SnapshotHierarchy set, File location) {
        set.invalidate(location.absolutePath)
    }

    private SnapshotHierarchy fileHierarchySet(File location) {
        EMPTY.store(location.absolutePath, location.directory ? snapshotDir(location) : snapshotFile(location))
    }

    private SnapshotHierarchy fileHierarchySet(Iterable<? extends File> locations) {
        SnapshotHierarchy set = EMPTY
        for (File location : locations) {
            set = set.store(location.absolutePath, location.directory ? snapshotDir(location) : snapshotFile(location))
        }
        return set
    }

    private SnapshotHierarchy updateDir(SnapshotHierarchy set, File dir) {
        set.store(dir.absolutePath, snapshotDir(dir))
    }

    private static List<String> flatten(SnapshotHierarchy set) {
        if (!(set instanceof DefaultSnapshotHierarchy)) {
            return []
        }
        List<String> prefixes = new ArrayList<>()
        collectPrefixes(set.rootNode, 0, prefixes)
        return prefixes
    }

    private static void collectPrefixes(FileSystemNode node, int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add((File.separator == '/' ? '/' : "") + node.getPathToParent())
        } else {
            prefixes.add(depth + ":" + node.getPathToParent().replace(File.separatorChar, (char) '/'))
        }
        List<? extends FileSystemNode> children
        def unpackedNode = (node.getSnapshot().filter { it instanceof CompleteFileSystemLocationSnapshot }.orElse(node))
        if (unpackedNode instanceof CompleteDirectorySnapshot) {
            children = unpackedNode.children
        } else if (unpackedNode instanceof AbstractIncompleteSnapshotWithChildren) {
            children = unpackedNode.children
        } else {
            children = []
        }
        children.forEach { child ->
            collectPrefixes(child, depth + 1, prefixes)
        }
    }
}
