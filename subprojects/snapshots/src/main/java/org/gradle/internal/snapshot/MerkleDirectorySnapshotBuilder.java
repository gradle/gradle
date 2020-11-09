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
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MerkleDirectorySnapshotBuilder {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    private final Deque<List<CompleteFileSystemLocationSnapshot>> levelHolder = new ArrayDeque<>();
    private final Deque<String> directoryAbsolutePaths = new ArrayDeque<>();
    private final boolean sortingRequired;
    private CompleteFileSystemLocationSnapshot result;

    public static MerkleDirectorySnapshotBuilder sortingRequired() {
        return new MerkleDirectorySnapshotBuilder(true);
    }

    public static MerkleDirectorySnapshotBuilder noSortingRequired() {
        return new MerkleDirectorySnapshotBuilder(false);
    }

    private MerkleDirectorySnapshotBuilder(boolean sortingRequired) {
        this.sortingRequired = sortingRequired;
    }

    public void preVisitDirectory(String absolutePath) {
        levelHolder.addLast(new ArrayList<>());
        directoryAbsolutePaths.addLast(absolutePath);
    }

    public void preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        String absolutePath = directorySnapshot.getAbsolutePath();
        preVisitDirectory(absolutePath);
    }

    public void visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
        snapshot.accept(new FileSystemLocationSnapshotVisitor() {
            @Override
            public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                visitNonDirectoryEntry(snapshot, isRoot);
            }

            @Override
            public void visitMissing(MissingFileSnapshot missingSnapshot) {
                visitNonDirectoryEntry(snapshot, isRoot);
            }
        });
    }

    private void visitNonDirectoryEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
        if (isRoot) {
            result = snapshot;
        } else {
            levelHolder.peekLast().add(snapshot);
        }
    }

    public void postVisitDirectory(AccessType accessType, String name) {
        postVisitDirectory(true, accessType, name);
    }

    public boolean postVisitDirectory(boolean includeEmpty, AccessType accessType, String name) {
        List<CompleteFileSystemLocationSnapshot> children = levelHolder.removeLast();
        String absolutePath = directoryAbsolutePaths.removeLast();
        if (children.isEmpty() && !includeEmpty) {
            return false;
        }
        if (sortingRequired) {
            children.sort(CompleteFileSystemLocationSnapshot.BY_NAME);
        }
        Hasher hasher = Hashing.newHasher();
        hasher.putHash(DIR_SIGNATURE);
        for (CompleteFileSystemLocationSnapshot child : children) {
            hasher.putString(child.getName());
            hasher.putHash(child.getHash());
        }
        CompleteDirectorySnapshot directorySnapshot = new CompleteDirectorySnapshot(absolutePath, name, accessType, hasher.hash(), children);
        List<CompleteFileSystemLocationSnapshot> siblings = levelHolder.peekLast();
        if (siblings != null) {
            siblings.add(directorySnapshot);
        } else {
            result = directorySnapshot;
        }
        return true;
    }

    @Nullable
    public CompleteFileSystemLocationSnapshot getResult() {
        return result;
    }
}
