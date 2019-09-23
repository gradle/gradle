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
        TestFile someFile
        temporaryFolder.file("some/subdir").create {
            someFile = file("someFile")
        }

        when:
        allowFileSystemAccess(true)
        def snapshot = readFromVfs(someFile)
        then:
        snapshot.type == FileType.RegularFile
        snapshot.absolutePath == someFile.absolutePath
        snapshot.hash.toString() == "f571933bb948577b1f9f3076fb8fc7c6"

        when:
        allowFileSystemAccess(false)
        snapshot = readFromVfs(someFile)
        then:
        snapshot.type == FileType.RegularFile
        snapshot.absolutePath == someFile.absolutePath
        snapshot.hash.toString() == "f571933bb948577b1f9f3076fb8fc7c6"

        def missingSubfile = someFile.file("subfile/which/is/deep.txt")
        when:
        snapshot = readFromVfs(missingSubfile)
        then:
        snapshot.type == FileType.Missing
        snapshot.absolutePath == missingSubfile.absolutePath
    }

    def "can read a missing file"() {
        TestFile someFile = temporaryFolder.file("some/subdir/someFile.txt")
        def regularParent = someFile.parentFile.createFile()

        when:
        allowFileSystemAccess(true)
        def snapshot = readFromVfs(someFile)
        then:
        snapshot.type == FileType.Missing
        snapshot.absolutePath == someFile.absolutePath
        snapshot.name == someFile.name

        when:
        fileHasher.allowHashing(false)
        stat.allowStat(false)
        snapshot = readFromVfs(someFile)
        then:
        snapshot.type == FileType.Missing
        snapshot.absolutePath == someFile.absolutePath
        snapshot.name == someFile.name

        def missingSubfile = someFile.file("subfile")
        when:
        snapshot = readFromVfs(missingSubfile)
        then:
        snapshot.type == FileType.Missing
        snapshot.absolutePath == missingSubfile.absolutePath

        when:
        allowFileSystemAccess(true)
        snapshot = readFromVfs(regularParent)
        then:
        snapshot.type == FileType.RegularFile
        snapshot.absolutePath == regularParent.absolutePath
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
        snapshot.type == FileType.Directory

        when:
        allowFileSystemAccess(false)
        snapshot = readFromVfs(someDir.file("sub"))
        then:
        snapshot.type == FileType.Directory
        snapshot.children*.name == ["inSub", "subsub"]
    }

    private allowFileSystemAccess(boolean allow) {
        fileHasher.allowHashing(allow)
        stat.allowStat(allow)
    }

    private FileSystemLocationSnapshot readFromVfs(File someFile) {
        def builder = MerkleDirectorySnapshotBuilder.sortingRequired()
        vfs.read(someFile.absolutePath, builder)
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
}
