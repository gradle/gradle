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

package org.gradle.internal.vfs;

import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;

import java.util.Collection;

/**
 * Listens to diffs to {@link CompleteFileSystemLocationSnapshot}s during an update of {@link SnapshotHierarchy}.
 *
 * Similar to {@link SnapshotHierarchy.NodeDiffListener}, only that
 * - it listens for {@link CompleteFileSystemLocationSnapshot}s and not {@link FileSystemNode}s.
 * - it receives all the changes for one update at once.
 */
public interface SnapshotDiffListener {
    SnapshotDiffListener NOOP = (removedSnapshots, addedSnapshots) -> { };

    /**
     * Called after the update to {@link SnapshotHierarchy} finished.
     *
     * Only the roots of added/removed hierarchies are reported.
     */
    void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots);
}
