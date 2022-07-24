/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.snapshot;

import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.EXCLUDE_EMPTY_DIRS;

/**
 * A builder for {@link DirectorySnapshot} instances.
 *
 * This implementation combines the hashes of the children of a directory into a single hash for the directory.
 * For the hash to be reproducible, the children must be sorted in a consistent order.
 * The implementation uses {@link FileSystemLocationSnapshot#BY_NAME} ordering.
 * If you already provide the children in sorted order, use {@link #noSortingRequired()} to avoid the overhead of sorting again.
 */
public class MerkleDirectorySnapshotBuilder implements DirectorySnapshotBuilder {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    private final Deque<Directory> directoryStack = new ArrayDeque<>();
    private final boolean sortingRequired;
    private FileSystemLocationSnapshot result;

    public static DirectorySnapshotBuilder sortingRequired() {
        return new MerkleDirectorySnapshotBuilder(true);
    }

    public static DirectorySnapshotBuilder noSortingRequired() {
        return new MerkleDirectorySnapshotBuilder(false);
    }

    protected MerkleDirectorySnapshotBuilder(boolean sortingRequired) {
        this.sortingRequired = sortingRequired;
    }

    @Override
    public void enterDirectory(AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy) {
        directoryStack.addLast(new Directory(accessType, absolutePath, name, emptyDirectoryHandlingStrategy));
    }

    @Override
    public void visitLeafElement(FileSystemLeafSnapshot snapshot) {
        collectEntry(snapshot);
    }

    @Override
    public void visitDirectory(DirectorySnapshot directorySnapshot) {
        collectEntry(directorySnapshot);
    }

    @Override
    public FileSystemLocationSnapshot leaveDirectory() {
        FileSystemLocationSnapshot snapshot = directoryStack.removeLast().fold();
        if (snapshot != null) {
            collectEntry(snapshot);
        }
        return snapshot;
    }

    private void collectEntry(FileSystemLocationSnapshot snapshot) {
        Directory directory = directoryStack.peekLast();
        if (directory != null) {
            directory.collectEntry(snapshot);
        } else {
            assert result == null;
            result = snapshot;
        }
    }

    @Override
    public FileSystemLocationSnapshot getResult() {
        return result;
    }

    private class Directory {
        private final AccessType accessType;
        private final String absolutePath;
        private final String name;
        private final List<FileSystemLocationSnapshot> children;
        private final EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy;

        public Directory(AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy) {
            this.accessType = accessType;
            this.absolutePath = absolutePath;
            this.name = name;
            this.children = new ArrayList<>();
            this.emptyDirectoryHandlingStrategy = emptyDirectoryHandlingStrategy;
        }

        public void collectEntry(FileSystemLocationSnapshot snapshot) {
            children.add(snapshot);
        }

        @Nullable
        public DirectorySnapshot fold() {
            if (emptyDirectoryHandlingStrategy == EXCLUDE_EMPTY_DIRS && children.isEmpty()) {
                return null;
            }
            if (sortingRequired) {
                children.sort(FileSystemLocationSnapshot.BY_NAME);
            }
            Hasher hasher = Hashing.newHasher();
            hasher.putHash(DIR_SIGNATURE);
            for (FileSystemLocationSnapshot child : children) {
                hasher.putString(child.getName());
                hasher.putHash(child.getHash());
            }
            return new DirectorySnapshot(absolutePath, name, accessType, hasher.hash(), children);
        }
    }
}
