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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public class FilteredTrackingMerkleDirectorySnapshotBuilder extends MerkleDirectorySnapshotBuilder {
    private final Deque<Boolean> isCurrentLevelUnfiltered = new ArrayDeque<>();
    private final Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer;

    public static FilteredTrackingMerkleDirectorySnapshotBuilder sortingRequired(Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer) {
        return new FilteredTrackingMerkleDirectorySnapshotBuilder(true, unfilteredSnapshotConsumer);
    }

    private FilteredTrackingMerkleDirectorySnapshotBuilder(boolean sortingRequired, Consumer<FileSystemLocationSnapshot> unfilteredSnapshotConsumer) {
        super(sortingRequired);
        this.unfilteredSnapshotConsumer = unfilteredSnapshotConsumer;
        // The root starts out as unfiltered.
        isCurrentLevelUnfiltered.addLast(true);
    }

    @Override
    public void enterDirectory(FileMetadata.AccessType accessType, String absolutePath, String name, EmptyDirectoryHandlingStrategy emptyDirectoryHandlingStrategy) {
        isCurrentLevelUnfiltered.addLast(true);
        super.enterDirectory(accessType, absolutePath, name, emptyDirectoryHandlingStrategy);
    }

    public void markCurrentLevelAsFiltered() {
        isCurrentLevelUnfiltered.removeLast();
        isCurrentLevelUnfiltered.addLast(false);
    }

    public boolean isCurrentLevelUnfiltered() {
        return isCurrentLevelUnfiltered.getLast();
    }

    @Override
    public FileSystemLocationSnapshot leaveDirectory() {
        FileSystemLocationSnapshot directorySnapshot = super.leaveDirectory();
        boolean leftLevelUnfiltered = isCurrentLevelUnfiltered.removeLast();
        isCurrentLevelUnfiltered.addLast(isCurrentLevelUnfiltered.removeLast() && leftLevelUnfiltered);
        if (!leftLevelUnfiltered && directorySnapshot != null) {
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
                        unfilteredSnapshotConsumer.accept(snapshot);
                    }

                    return SnapshotVisitResult.SKIP_SUBTREE;
                }
            });
        }
        return directorySnapshot;
    }
}
