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

package org.gradle.internal.watch.registry;

import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.WatchingNotSupportedException;

import java.io.File;
import java.util.Collection;

public interface FileWatcherUpdater extends SnapshotHierarchy.SnapshotDiffListener {
    /**
     * Registers a hierarchy to watch.
     *
     * The watcher registry will only watch for changes in hierarchies to watch.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    void registerHierarchyToWatch(File hierarchyToWatch, SnapshotHierarchy root);

    /**
     * Updates the watchers after changes to the root.
     *
     * {@inheritDoc}.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    @Override
    void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root);

    /**
     * Remove everything from the root which can't be kept after the current build finished.
     *
     * This is anything which is not within a watched hierarchy or in a cache directory.
     */
    SnapshotHierarchy buildFinished(SnapshotHierarchy root);
}
