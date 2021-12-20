/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public class IncompleteTrackingMerkleDirectorySnapshotBuilder extends MerkleDirectorySnapshotBuilder {
    private final Deque<Boolean> isCurrentLevelComplete = new ArrayDeque<>();

    public static IncompleteTrackingMerkleDirectorySnapshotBuilder sortingRequired() {
        return new IncompleteTrackingMerkleDirectorySnapshotBuilder(true);
    }

    private IncompleteTrackingMerkleDirectorySnapshotBuilder(boolean sortingRequired) {
        super(sortingRequired);
        // The root starts out as complete.
        isCurrentLevelComplete.addLast(true);
    }

    @Override
    public void enterDirectory(FileMetadata.AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy) {
        isCurrentLevelComplete.addLast(true);
        super.enterDirectory(accessType, absolutePath, name, emptyDirectoryHandlingStrategy);
    }

    public void markCurrentLevelAsIncomplete() {
        isCurrentLevelComplete.removeLast();
        isCurrentLevelComplete.addLast(false);
    }

    public boolean isCurrentLevelComplete() {
        return isCurrentLevelComplete.getLast();
    }

    @Override
    public FileSystemLocationSnapshot leaveDirectory() {
        return leaveDirectory(snapshot -> {});
    }

    @Nullable
    public FileSystemLocationSnapshot leaveDirectory(Consumer<FileSystemLocationSnapshot> incompleteSnapshotConsumer) {
        FileSystemLocationSnapshot directorySnapshot = super.leaveDirectory();
        boolean leftLevelComplete = isCurrentLevelComplete.removeLast();
        isCurrentLevelComplete.addLast(isCurrentLevelComplete.removeLast() && leftLevelComplete);
        if (!leftLevelComplete && directorySnapshot != null) {
            directorySnapshot.accept(new FileSystemSnapshotHierarchyVisitor() {
                private boolean isRoot = true;

                @Override
                public void enterDirectory(DirectorySnapshot directorySnapshot) {
                    isRoot = false;
                }

                @Override
                public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
                    if (isRoot) {
                        return SnapshotVisitResult.CONTINUE;
                    } else {
                        incompleteSnapshotConsumer.accept(snapshot);
                    }

                    return SnapshotVisitResult.SKIP_SUBTREE;
                }
            });
        }
        return directorySnapshot;
    }
}
