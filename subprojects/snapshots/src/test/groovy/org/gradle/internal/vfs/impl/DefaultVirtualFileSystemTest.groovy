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


import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.test.fixtures.file.TestFile

class DefaultVirtualFileSystemTest extends AbstractVirtualFileSystemTest {

    def "can read a file"() {
        TestFile someFile = temporaryFolder.file("some/subdir/someFile").createFile()

        when:
        allowFileSystemAccess(true)
        def snapshot = readFromVfs(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        snapshot = readFromVfs(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        def missingSubfile = someFile.file("subfile/which/is/deep.txt")
        when:
        snapshot = readFromVfs(missingSubfile)
        then:
        assertIsMissingFileSnapshot(snapshot, missingSubfile)
    }

    def "can read a missing file"() {
        TestFile someFile = temporaryFolder.file("some/subdir/someFile.txt")
        def regularParent = someFile.parentFile.createFile()

        when:
        allowFileSystemAccess(true)
        def snapshot = readFromVfs(someFile)
        then:
        assertIsMissingFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        snapshot = readFromVfs(someFile)
        then:
        assertIsMissingFileSnapshot(snapshot, someFile)

        def missingSubfile = someFile.file("subfile")
        when:
        snapshot = readFromVfs(missingSubfile)
        then:
        assertIsMissingFileSnapshot(snapshot, missingSubfile)

        when:
        allowFileSystemAccess(true)
        snapshot = readFromVfs(regularParent)
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
        def snapshot = readFromVfs(someDir)
        then:
        assertIsDirectorySnapshot(snapshot, someDir)

        when:
        allowFileSystemAccess(false)
        def subDir = someDir.file("sub")
        snapshot = readFromVfs(subDir)
        then:
        assertIsDirectorySnapshot(snapshot, subDir)
    }

    def "invalidate regular file"() {
        def parentDir = temporaryFolder.file("in/some")
        def someFile = parentDir.file("directory/somefile.txt").createFile()
        when:
        allowFileSystemAccess(true)
        def snapshot = readFromVfs(someFile)
        then:
        assertIsFileSnapshot(snapshot, someFile)

        when:
        allowFileSystemAccess(false)
        vfs.update([someFile.absolutePath]) {
            someFile << "Updated"
        }

        and:
        allowFileSystemAccess(true)
        snapshot = readFromVfs(someFile)

        then:
        someFile.text == "Updated"
        assertIsFileSnapshot(snapshot, someFile)

        when:
        snapshot = readFromVfs(parentDir)
        allowFileSystemAccess(false)
        then:
        assertIsDirectorySnapshot(snapshot, parentDir)
        and:
        assertIsFileSnapshot(readFromVfs(someFile), someFile)

        when:
        vfs.update([someFile.absolutePath]) {
            someFile.text = "Updated again"
        }
        and:
        allowFileSystemAccess(true)
        snapshot = readFromVfs(someFile)
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
        def snapshot = readFromVfs(dir)
        then:
        assertIsDirectorySnapshot(snapshot, dir)
        assertIsFileSnapshot(readFromVfs(existingFileInDir), existingFileInDir)

        when:
        allowFileSystemAccess(false)
        vfs.update([nonExistingFileInDir.absolutePath]) {
            nonExistingFileInDir.text = "created"
        }
        then:
        assertIsFileSnapshot(readFromVfs(existingFileInDir), existingFileInDir)

        when:
        allowFileSystemAccess(true)
        snapshot = readFromVfs(nonExistingFileInDir)
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
        def snapshot = readFromVfs(d, new FileNameFilter({ name -> name.endsWith('1')}))
        def visitor = new RelativePathCapturingVisitor()
        snapshot.accept(visitor)
        then:
        assertIsDirectorySnapshot(snapshot, d)
        visitor.relativePaths == ["d1", "d1/f1", "f1"]

        when:
        // filtered snapshots are currently not stored in the VFS
        allowFileSystemAccess(true)
        snapshot = readFromVfs(includedFile)
        then:
        assertIsFileSnapshot(snapshot, includedFile)

        when:
        snapshot = readFromVfs(excludedFile)
        then:
        assertIsFileSnapshot(snapshot, excludedFile)
    }

    def "reuses cached unfiltered trees when looking for details of a filtered tree"() {
        given: "An existing snapshot"
        def d = temporaryFolder.createDir("d")
        d.file("f1").createFile()
        d.file("d1/f1").createFile()
        d.file("d1/f2").createFile()

        allowFileSystemAccess(true)
        readFromVfs(d)

        and: "A filtered tree over the same directory"
        def patterns = new FileNameFilter({ it.endsWith('1') })

        when:
        allowFileSystemAccess(false)
        def snapshot = readFromVfs(d, patterns)
        def relativePaths = [] as Set
        snapshot.accept(new FileSystemSnapshotVisitor() {
            private Deque<String> relativePath = new ArrayDeque<String>()
            private boolean seenRoot = false

            @Override
            boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                if (!seenRoot) {
                    seenRoot = true
                } else {
                    relativePath.addLast(directorySnapshot.name)
                    relativePaths.add(relativePath.join("/"))
                }
                return true
            }

            @Override
            void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                relativePath.addLast(fileSnapshot.name)
                relativePaths.add(relativePath.join("/"))
                relativePath.removeLast()
            }

            @Override
            void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
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
}
