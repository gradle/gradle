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

import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchyReference;
import org.gradle.internal.vfs.SnapshotHierarchy;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DelegatingDiffCapturingUpdateFunctionDecorator implements SnapshotHierarchy.DiffCapturingUpdateFunctionDecorator {

    private SnapshotHierarchy.CollectedDiffListener collectedDiffListener;

    public void setCollectedDiffListener(@Nullable SnapshotHierarchy.CollectedDiffListener collectedDiffListener) {
        this.collectedDiffListener = collectedDiffListener;
    }

    @Override
    public SnapshotHierarchyReference.UpdateFunction decorate(SnapshotHierarchy.DiffCapturingUpdateFunction updateFunction) {
        SnapshotHierarchy.CollectedDiffListener currentListener = collectedDiffListener;
        if (currentListener == null) {
            return root -> updateFunction.update(root, SnapshotHierarchy.NodeDiffListener.NOOP);
        }

        CollectingDiffListener listener = new CollectingDiffListener(currentListener);
        return root -> {
            SnapshotHierarchy newRoot = updateFunction.update(root, listener);
            listener.publishCollectedDiff();
            return newRoot;
        };
    }

    private static class CollectingDiffListener implements SnapshotHierarchy.NodeDiffListener {
        private final List<FileSystemNode> removedNodes;
        private final List<FileSystemNode> addedNodes;
        private final SnapshotHierarchy.CollectedDiffListener currentListener;

        public CollectingDiffListener(SnapshotHierarchy.CollectedDiffListener currentListener) {
            this.currentListener = currentListener;
            removedNodes = new ArrayList<>();
            addedNodes = new ArrayList<>();
        }

        public void publishCollectedDiff() {
            currentListener.changed(removedNodes, addedNodes);
        }

        @Override
        public void nodeRemoved(FileSystemNode node) {
            removedNodes.add(node);
        }

        @Override
        public void nodeAdded(FileSystemNode node) {
            addedNodes.add(node);
        }
    }
}
