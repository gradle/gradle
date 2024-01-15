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


import org.gradle.internal.snapshot.SnapshotUtil
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.test.fixtures.file.TestFile

class DefaultFileSystemAccessTest extends AbstractFileSystemAccessTest {

    def "can read a file"() {
        TestFile someFile = temporaryFolder.file("some/subdir/someFile").createFile()

        when:
        allowFileSystemAccess(true)
        def snapshot = read(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        snapshot = read(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        def missingSubfile = someFile.file("subfile/which/is/deep.txt")
        when:
        snapshot = read(missingSubfile)
        then:
        assertIsMissingFileSnapshot(snapshot, missingSubfile)
    }

    def "can read a missing file"() {
        TestFile someFile = temporaryFolder.file("some/subdir/someFile.txt")
        def regularParent = someFile.parentFile.createFile()

        when:
        allowFileSystemAccess(true)
        def snapshot = read(someFile)
        then:
        assertIsMissingFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        snapshot = read(someFile)
        then:
        assertIsMissingFileSnapshot(snapshot, someFile)

        def missingSubfile = someFile.file("subfile")
        when:
        snapshot = read(missingSubfile)
        then:
        assertIsMissingFileSnapshot(snapshot, missingSubfile)

        when:
        allowFileSystemAccess(true)
        snapshot = read(regularParent)
        then:
        assertIsFileSnapshot(snapshot, regularParent)
    }

    def "can read a directory"() {
        TestFile someDir = temporaryFolder.file("some/path/to/dir").create {
            dir("sub") {
                file("inSub")
                dir("subsub") {
                    file("inSubSub")
                }
            }
            file("inDir")
            dir("sibling") {
                file("inSibling")
            }
        }

        when:
        allowFileSystemAccess(true)
        def snapshot = read(someDir)
        then:
        assertIsDirectorySnapshot(snapshot, someDir)

        when:
        allowFileSystemAccess(false)
        def subDir = someDir.file("sub")
        snapshot = read(subDir)
        then:
        assertIsDirectorySnapshot(snapshot, subDir)
    }

    def "invalidate regular file"() {
        def parentDir = temporaryFolder.file("in/some")
        def someFile = parentDir.file("directory/somefile.txt").createFile()
        when:
        allowFileSystemAccess(true)
        def snapshot = read(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        fileSystemAccess.write([someFile.absolutePath]) {
            someFile << "Updated"
        }

        and:
        allowFileSystemAccess(true)
        snapshot = read(someFile)

        then:
        someFile.text == "Updated"
        assertIsFileSnapshot(snapshot, someFile)

        when:
        snapshot = read(parentDir)
        allowFileSystemAccess(false)
        then:
        assertIsDirectorySnapshot(snapshot, parentDir)
        and:
        assertIsFileSnapshot(read(someFile), someFile)

        when:
        fileSystemAccess.write([someFile.absolutePath]) {
            someFile.text = "Updated again"
        }
        and:
        allowFileSystemAccess(true)
        snapshot = read(someFile)
        then:
        someFile.text == "Updated again"
        assertIsFileSnapshot(snapshot, someFile)
    }

    def "can invalidate non-existing file in known directory"() {
        def dir = temporaryFolder.createDir("some/dir")
        def existingFileInDir = dir.file("someFile.txt").createFile()
        def nonExistingFileInDir = dir.file("subdir/nonExisting.txt")

        when:
        allowFileSystemAccess(true)
        def snapshot = read(dir)
        then:
        assertIsDirectorySnapshot(snapshot, dir)
        assertIsFileSnapshot(read(existingFileInDir), existingFileInDir)

        when:
        allowFileSystemAccess(false)
        fileSystemAccess.write([nonExistingFileInDir.absolutePath]) {
            nonExistingFileInDir.text = "created"
        }
        then:
        assertIsFileSnapshot(read(existingFileInDir), existingFileInDir)

        when:
        allowFileSystemAccess(true)
        snapshot = read(nonExistingFileInDir)
        then:
        assertIsFileSnapshot(snapshot, nonExistingFileInDir)
    }

    def "can filter parts of the filesystem"() {
        def d = temporaryFolder.createDir("d")
        d.createFile("f1")
        def excludedFile = d.createFile("d1/f2")
        def includedFile = d.createFile("d1/f1")

        when:
        allowFileSystemAccess(true)
        def snapshot = read(d, new FileNameFilter({ name -> name.endsWith('1')}))
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)
        then:
        assertIsDirectorySnapshot(snapshot, d)
        relativePaths == ["d1", "d1/f1", "f1"]

        when:
        // filtered snapshots are currently not stored in the VFS
        allowFileSystemAccess(true)
        snapshot = read(includedFile)
        then:
        assertIsFileSnapshot(snapshot, includedFile)

        when:
        snapshot = read(excludedFile)
        then:
        assertIsFileSnapshot(snapshot, excludedFile)
    }

    def "can read a filtered tree of #type including the file"() {
        def file = temporaryFolder."${method}"("file")
        when:
        allowFileSystemAccess(true)
        def snapshot = read(file, new FileNameFilter({ true }))
        then:
        snapshot == read(file)

        where:
        type           | method
        'missing file' | 'file'
        'regular file' | 'createFile'
    }

    def "can read a filtered tree of #type excluding the file"() {
        def file = temporaryFolder."${method}"("file")
        when:
        allowFileSystemAccess(true)
        def snapshot = read(file, new FileNameFilter({ false }))
        then:
        snapshot == null

        where:
        type           | method
        'missing file' | 'file'
        'regular file' | 'createFile'
    }

    def "reuses cached unfiltered trees when looking for details of a filtered tree"() {
        given: "An existing snapshot"
        def d = temporaryFolder.createDir("d")
        d.file("f1").createFile()
        d.file("d1/f1").createFile()
        d.file("d1/f2").createFile()

        allowFileSystemAccess(true)
        read(d)

        and: "A filtered tree over the same directory"
        def patterns = new FileNameFilter({ it.endsWith('1') })

        when:
        allowFileSystemAccess(false)
        def snapshot = read(d, patterns)
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(snapshot)

        then: "The filtered tree uses the cached state"
        relativePaths as Set == ["d1", "d1/f1", "f1"] as Set
    }

    def "reuses cached descendant tree on read"() {
        given:
        def parentDir = temporaryFolder.createDir("parent")
        def childDir = parentDir.createDir("child")
        childDir.file("file.txt").createFile()

        allowFileSystemAccess(true)
        def childSnapshot = read(childDir)

        when:
        def parentSnapshot = read(parentDir)
        def index = SnapshotUtil.indexByAbsolutePath(parentSnapshot)

        then:
        index[childDir.absolutePath].is(childSnapshot)
    }

    def "reuses cached descendant tree when it has siblings on read"() {
        given:
        def parentDir = temporaryFolder.createDir("parent")
        def childDir = parentDir.createDir("child")
        parentDir.createDir("sibling")
        childDir.file("file.txt").createFile()

        allowFileSystemAccess(true)
        def childSnapshot = read(childDir)

        when:
        def parentSnapshot = read(parentDir)
        def index = SnapshotUtil.indexByAbsolutePath(parentSnapshot)

        then:
        index[childDir.absolutePath].is(childSnapshot)
    }

    def "reuses cached deep descendant trees"() {
        given:
        def parentDir = temporaryFolder.createDir("parent")
        def childDir = parentDir.createDir("child")
        def grandChildDir = parentDir.createDir("grand-child")
        parentDir.createDir("sibling")
        def cousinFile = childDir.createFile("cousin.txt")
        childDir.createFile("other-cousin.txt")
        grandChildDir.file("file.txt").createFile()

        allowFileSystemAccess(true)
        def grandChildSnapshot = read(grandChildDir)
        def cousinSnapshot = read(cousinFile)

        when:
        def parentSnapshot = read(parentDir)
        def index = SnapshotUtil.indexByAbsolutePath(parentSnapshot)

        then:
        index[grandChildDir.absolutePath].is(grandChildSnapshot)
        index[cousinFile.absolutePath].is(cousinSnapshot)
    }

    def "reuses cached unfiltered descendant trees when looking for details of a filtered tree"() {
        given:
        def parentDir = temporaryFolder.createDir("parent")
        def childDir = parentDir.createDir("child")
        childDir.createFile("file.txt")
        childDir.createFile("file.html")

        allowFileSystemAccess(true)
        read(childDir)

        and:
        def patterns = new FileNameFilter({ it.endsWith('.txt') })

        when:
        def parentSnapshot = read(parentDir, patterns)
        def relativePaths = SnapshotVisitorUtil.getRelativePaths(parentSnapshot)
        then:
        relativePaths as Set == ["child", "child/file.txt"] as Set
    }
}
