/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SnapshotCollectingDiffListener implements SnapshotHierarchy.NodeDiffListener {
    private final List<CompleteFileSystemLocationSnapshot> removedSnapshots = new ArrayList<>();
    private final List<CompleteFileSystemLocationSnapshot> addedSnapshots = new ArrayList<>();
    private final Predicate<String> watchFilter;

    public SnapshotCollectingDiffListener(Predicate<String> watchFilter) {
        this.watchFilter = watchFilter;
    }

    public void publishSnapshotDiff(SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener) {
        if (!removedSnapshots.isEmpty() || !addedSnapshots.isEmpty()) {
            snapshotDiffListener.changed(removedSnapshots, addedSnapshots);
        }
    }

    private void extractRootSnapshots(FileSystemNode rootNode, Consumer<CompleteFileSystemLocationSnapshot> consumer) {
        rootNode.accept(snapshot -> {
            if (snapshot.getAccessType() == AccessType.DIRECT && watchFilter.test(snapshot.getAbsolutePath())) {
                consumer.accept(snapshot);
            }
        });
    }

    @Override
    public void nodeRemoved(FileSystemNode node) {
        extractRootSnapshots(node, removedSnapshots::add);
    }

    @Override
    public void nodeAdded(FileSystemNode node) {
        extractRootSnapshots(node, addedSnapshots::add);
    }
}
