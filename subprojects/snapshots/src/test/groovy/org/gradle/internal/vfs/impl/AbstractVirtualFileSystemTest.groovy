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
import org.gradle.internal.MutableReference
import org.gradle.internal.file.FileException
import org.gradle.internal.file.FileMetadataSnapshot
import org.gradle.internal.file.FileType
import org.gradle.internal.file.Stat
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.snapshot.SnapshottingFilter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.function.Predicate

@CleanupTestDirectory
abstract class AbstractVirtualFileSystemTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def fileHasher = new AllowingHasher(TestFiles.fileHasher())
    def stat = new AllowingStat(TestFiles.fileSystem())
    def vfs = new DefaultVirtualFileSystem(fileHasher, new StringInterner(), stat, Executors.newFixedThreadPool(1))

    def cleanup() {
        vfs.close()
    }

    void allowFileSystemAccess(boolean allow) {
        fileHasher.allowHashing(allow)
        stat.allowStat(allow)
    }

    FileSystemLocationSnapshot readFromVfs(File file) {
        MutableReference<FileSystemLocationSnapshot> result = MutableReference.empty()
        vfs.read(file.absolutePath, result.&set)
        return result.get()
    }

    FileSystemLocationSnapshot readFromVfs(File file, SnapshottingFilter filter) {
        MutableReference<FileSystemLocationSnapshot> result = MutableReference.empty()
        vfs.read(file.absolutePath, filter, result.&set)
        return result.get()
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

    HashCode hashFile(File file) {
        TestFiles.fileHasher().hash(file)
    }

    static class FileNameFilter implements SnapshottingFilter {
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
