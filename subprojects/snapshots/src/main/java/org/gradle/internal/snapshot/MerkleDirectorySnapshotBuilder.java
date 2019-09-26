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

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MerkleDirectorySnapshotBuilder implements FileSystemSnapshotVisitor {
    private static final HashCode DIR_SIGNATURE = Hashing.signature("DIR");

    private final RelativePathSegmentsTracker relativePathSegmentsTracker = new RelativePathSegmentsTracker();
    private final Deque<List<FileSystemLocationSnapshot>> levelHolder = new ArrayDeque<>();
    private final Deque<String> directoryAbsolutePaths = new ArrayDeque<>();
    private final boolean sortingRequired;
    private FileSystemLocationSnapshot result;

    public static MerkleDirectorySnapshotBuilder sortingRequired() {
        return new MerkleDirectorySnapshotBuilder(true);
    }

    public static MerkleDirectorySnapshotBuilder noSortingRequired() {
        return new MerkleDirectorySnapshotBuilder(false);
    }

    private MerkleDirectorySnapshotBuilder(boolean sortingRequired) {
        this.sortingRequired = sortingRequired;
    }

    public void preVisitDirectory(String absolutePath, String name) {
        relativePathSegmentsTracker.enter(name);
        levelHolder.addLast(new ArrayList<>());
        directoryAbsolutePaths.addLast(absolutePath);
    }

    @Override
    public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
        preVisitDirectory(directorySnapshot.getAbsolutePath(), directorySnapshot.getName());
        return true;
    }

    @Override
    public void visitFile(FileSystemLocationSnapshot fileSnapshot) {
        if (relativePathSegmentsTracker.isRoot()) {
            result = fileSnapshot;
        } else {
            levelHolder.peekLast().add(fileSnapshot);
        }
    }

    @Override
    public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
        postVisitDirectory(true);
    }

    public void postVisitDirectory() {
        postVisitDirectory(true);
    }

    public boolean postVisitDirectory(boolean includeEmpty) {
        String name = relativePathSegmentsTracker.leave();
        List<FileSystemLocationSnapshot> children = levelHolder.removeLast();
        String absolutePath = directoryAbsolutePaths.removeLast();
        if (children.isEmpty() && !includeEmpty) {
            return false;
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
        DirectorySnapshot directorySnapshot = new DirectorySnapshot(absolutePath, name, children, hasher.hash());
        List<FileSystemLocationSnapshot> siblings = levelHolder.peekLast();
        if (siblings != null) {
            siblings.add(directorySnapshot);
        } else {
            result = directorySnapshot;
        }
        return true;
    }

    public boolean isRoot() {
        return relativePathSegmentsTracker.isRoot();
    }

    public Iterable<String> getRelativePath() {
        return relativePathSegmentsTracker.getRelativePath();
    }

    @Nullable
    public FileSystemLocationSnapshot getResult() {
        return result;
    }

    /**
     * If the snapshot which is visited is a merkle directory snapshot, no need to make a copy.
     */
    public void setResult(FileSystemLocationSnapshot result) {
        this.result = result;
    }
}
