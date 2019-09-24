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
import org.gradle.internal.file.FileException
import org.gradle.internal.file.FileMetadataSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.file.Stat
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder
import org.gradle.internal.snapshot.impl.DirectorySnapshotter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class DefaultVirtualFileSystemTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def fileHasher = new AllowingHasher(TestFiles.fileHasher())
    def stat = new AllowingStat(TestFiles.fileSystem())
    def vfs = new DefaultVirtualFileSystem(stat, new DirectorySnapshotter(fileHasher, new StringInterner()), fileHasher)

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

    private allowFileSystemAccess(boolean allow) {
        fileHasher.allowHashing(allow)
        stat.allowStat(allow)
    }

    private FileSystemLocationSnapshot readFromVfs(File file) {
        def builder = MerkleDirectorySnapshotBuilder.sortingRequired()
        vfs.read(file.absolutePath, builder)
        return builder.result
    }

    static class AllowingHasher implements FileHasher {

        private final FileHasher delegate
        private boolean hashingAllowed

        AllowingHasher(FileHasher delegate) {
            this.delegate = delegate
        }

        @Override
        HashCode hash(File file) {
            checkIfAllowed()
            return delegate.hash(file)
        }

        @Override
        HashCode hash(File file, long length, long lastModified) {
            checkIfAllowed()
            return delegate.hash(file, length, lastModified)
        }

        private void checkIfAllowed() {
            if (!hashingAllowed) {
                throw new UnsupportedOperationException("Hashing is currently not allowed")
            }
        }

        void allowHashing(boolean allowed) {
            this.hashingAllowed = allowed
        }
    }

    static class AllowingStat implements Stat {

        private final Stat delegate
        private boolean statAllowed

        AllowingStat(Stat delegate) {
            this.delegate = delegate
        }

        @Override
        int getUnixMode(File f) throws FileException {
            checkIfAllowed()
            return delegate.getUnixMode(f)
        }

        @Override
        FileMetadataSnapshot stat(File f) throws FileException {
            checkIfAllowed()
            return delegate.stat(f)
        }

        private void checkIfAllowed() {
            if (!statAllowed) {
                throw new UnsupportedOperationException("Stat is currently not allowed")
            }
        }

        void allowStat(boolean allowed) {
            this.statAllowed = allowed
        }
    }

    void assertIsFileSnapshot(FileSystemLocationSnapshot snapshot, File file) {
        assert snapshot.absolutePath == file.absolutePath
        assert snapshot.name == file.name
        assert snapshot.type == FileType.RegularFile
        assert snapshot.hash == hashFile(file)
    }

    void assertIsMissingFileSnapshot(FileSystemLocationSnapshot snapshot, File file) {
        assert snapshot.absolutePath == file.absolutePath
        assert snapshot.name == file.name
        assert snapshot.type == FileType.Missing
    }

    void assertIsDirectorySnapshot(FileSystemLocationSnapshot snapshot, File file) {
        assert snapshot.absolutePath == file.absolutePath
        assert snapshot.name == file.name
        assert snapshot.type == FileType.Directory
        assert (((DirectorySnapshot) snapshot).children*.name as Set) == (file.list() as Set)
    }

    private HashCode hashFile(File file) {
        TestFiles.fileHasher().hash(file)
    }
}
