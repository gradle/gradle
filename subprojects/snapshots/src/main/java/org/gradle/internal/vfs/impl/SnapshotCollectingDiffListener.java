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

import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

class SnapshotCollectingDiffListener implements SnapshotHierarchy.NodeDiffListener {
    private final List<CompleteFileSystemLocationSnapshot> removedSnapshots;
    private final List<CompleteFileSystemLocationSnapshot> addedSnapshots;
    private final Predicate<String> watchFilter;

    public SnapshotCollectingDiffListener(Predicate<String> watchFilter) {
        this.watchFilter = watchFilter;
        removedSnapshots = new ArrayList<>();
        addedSnapshots = new ArrayList<>();
    }

    public SnapshotHierarchy publishSnapshotDiff(SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener, SnapshotHierarchy newRoot) {
        if (!removedSnapshots.isEmpty() || !addedSnapshots.isEmpty()) {
            return snapshotDiffListener.changed(removedSnapshots, addedSnapshots, newRoot);
        }
        return newRoot;
    }

    private void extractRootSnapshots(FileSystemNode rootNode, Consumer<CompleteFileSystemLocationSnapshot> consumer) {
        rootNode.accept(snapshot -> {
            if (watchFilter.test(snapshot.getAbsolutePath())) {
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
