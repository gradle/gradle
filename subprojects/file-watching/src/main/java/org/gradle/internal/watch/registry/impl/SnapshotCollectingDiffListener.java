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

package org.gradle.internal.watch.registry.impl;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.util.ArrayList;
import java.util.List;

public class SnapshotCollectingDiffListener implements SnapshotHierarchy.NodeDiffListener {
    private final List<FileSystemLocationSnapshot> removedSnapshots = new ArrayList<>();
    private final List<FileSystemLocationSnapshot> addedSnapshots = new ArrayList<>();

    public void publishSnapshotDiff(SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener) {
        if (!removedSnapshots.isEmpty() || !addedSnapshots.isEmpty()) {
            snapshotDiffListener.changed(removedSnapshots, addedSnapshots);
        }
    }

    @Override
    public void nodeRemoved(FileSystemNode node) {
        node.rootSnapshots()
            .forEach(removedSnapshots::add);
    }

    @Override
    public void nodeAdded(FileSystemNode node) {
        node.rootSnapshots()
            .forEach(addedSnapshots::add);
    }
}
