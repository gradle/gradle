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

package org.gradle.internal.vfs;

import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MetadataSnapshot;

import javax.annotation.CheckReturnValue;
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

    /**
     * Returns a hierarchy augmented by the information of the snapshot at the absolute path.
     */
    @CheckReturnValue
    SnapshotHierarchy store(String absolutePath, MetadataSnapshot snapshot);

    /**
     * Returns a hierarchy without any information at the absolute path.
     */
    @CheckReturnValue
    SnapshotHierarchy invalidate(String absolutePath);

    /**
     * The empty hierarchy.
     */
    @CheckReturnValue
    SnapshotHierarchy empty();

    void visitSnapshots(SnapshotVisitor snapshotVisitor);

    interface SnapshotVisitor {
        void visitSnapshot(CompleteFileSystemLocationSnapshot snapshot, boolean rootOfCompleteHierarchy);
    }
}
