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
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Predicate

@CleanupTestDirectory
class DefaultVirtualFileSystemTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def fileHasher = new AllowingHasher(TestFiles.fileHasher())
    def stat = new AllowingStat(TestFiles.fileSystem())
    def vfs = new DefaultVirtualFileSystem(fileHasher, new StringInterner(), stat)

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
        allowFileSystemAccess(false)
        snapshot = readFromVfs(includedFile)
        then:
        assertIsFileSnapshot(snapshot, includedFile)

        when:
        // Currently, we snapshot everything not respecting any filters.
        // When this changes, a FS access will be necessary here.
        // allowFileSystemAccess(true)
        snapshot = readFromVfs(excludedFile)
        then:
        assertIsFileSnapshot(snapshot, excludedFile)
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

    private FileSystemLocationSnapshot readFromVfs(File file, SnapshottingFilter filter) {
        def builder = MerkleDirectorySnapshotBuilder.sortingRequired()
        vfs.read(file.absolutePath, filter, builder)
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

    static class RelativePathCapturingVisitor implements FileSystemSnapshotVisitor {
        private Deque<String> relativePath = new ArrayDeque<String>()
        private boolean seenRoot = false
        private final List<String> relativePaths = []

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

        List<String> getRelativePaths() {
            return relativePaths
        }
    }
}
