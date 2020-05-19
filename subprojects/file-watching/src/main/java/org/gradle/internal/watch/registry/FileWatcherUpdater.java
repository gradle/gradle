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
     * Changes the project root directory, e.g. when the same daemon is used on a different project.
     *
     * Hierarchical watchers use project root directory to minimize the number of watched roots
     * by watching the project root instead of watching directories inside.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    void updateProjectRootDirectory(File projectRoot);

    /**
     * {@inheritDoc}.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    @Override
    void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots);
}
