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

package org.gradle.internal.snapshot;

import javax.annotation.CheckReturnValue;
import java.util.Collection;
import java.util.Optional;

/**
 * An immutable hierarchy of snapshots of the file system.
 *
 * Intended to be to store an in-memory representation of the state of the file system.
 */
public interface SnapshotHierarchy {

    /**
     * Returns the snapshot stored at the absolute path.
     */
    Optional<MetadataSnapshot> getMetadata(String absolutePath);

    /**
     * Returns the complete snapshot stored at the absolute path.
     */
    default Optional<CompleteFileSystemLocationSnapshot> getSnapshot(String absolutePath) {
        return getMetadata(absolutePath)
            .filter(CompleteFileSystemLocationSnapshot.class::isInstance)
            .map(CompleteFileSystemLocationSnapshot.class::cast);
    }

    boolean hasDescendantsUnder(String absolutePath);

    /**
     * Returns a hierarchy augmented by the information of the snapshot at the absolute path.
     */
    @CheckReturnValue
    SnapshotHierarchy store(String absolutePath, MetadataSnapshot snapshot, NodeDiffListener diffListener);

    /**
     * Returns a hierarchy without any information at the absolute path.
     */
    @CheckReturnValue
    SnapshotHierarchy invalidate(String absolutePath, NodeDiffListener diffListener);

    /**
     * The empty hierarchy.
     */
    @CheckReturnValue
    SnapshotHierarchy empty();

    void visitSnapshotRoots(SnapshotVisitor snapshotVisitor);

    void visitSnapshotRoots(String absolutePath, SnapshotVisitor snapshotVisitor);

    interface SnapshotVisitor {
        void visitSnapshotRoot(CompleteFileSystemLocationSnapshot snapshot);
    }

    /**
     * Receives diff when a {@link SnapshotHierarchy} is updated.
     *
     * Only the root nodes which have been removed/added are reported.
     */
    interface NodeDiffListener {
        NodeDiffListener NOOP = new NodeDiffListener() {
            @Override
            public void nodeRemoved(FileSystemNode node) {
            }

            @Override
            public void nodeAdded(FileSystemNode node) {
            }
        };

        /**
         * Called when a node is removed during the update.
         *
         * Only called for the node which is removed, and not every node in the hierarchy which is removed.
         */
        void nodeRemoved(FileSystemNode node);

        /**
         * Called when a node is added during the update.
         *
         * Only called for the node which is added, and not every node in the hierarchy which is added.
         */
        void nodeAdded(FileSystemNode node);
    }

    /**
     * Listens to diffs to {@link CompleteFileSystemLocationSnapshot}s during an update of {@link SnapshotHierarchy}.
     *
     * Similar to {@link NodeDiffListener}, only that
     * - it listens for {@link CompleteFileSystemLocationSnapshot}s and not {@link FileSystemNode}s.
     * - it receives all the changes for one update at once.
     */
    interface SnapshotDiffListener {
        SnapshotDiffListener NOOP = (removedSnapshots, addedSnapshots) -> {};

        /**
         * Called after the update to {@link SnapshotHierarchy} finished.
         *
         * Only the roots of added/removed hierarchies are reported.
         */
        void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots);
    }
}
