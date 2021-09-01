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

package org.gradle.internal.watch.registry.impl;

import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.vfs.WatchMode;

import javax.annotation.CheckReturnValue;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    protected final FileWatcher fileWatcher;
    protected final WatchableHierarchies watchableHierarchies;

    public AbstractFileWatcherUpdater(FileWatcher fileWatcher, WatchableHierarchies watchableHierarchies) {
        this.fileWatcher = fileWatcher;
        this.watchableHierarchies = watchableHierarchies;
    }

    @Override
    public final SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode) {
        watchableHierarchies.updateUnsupportedFileSystems(watchMode);
        return doUpdateVfsOnBuildStarted(root);
    }

    @CheckReturnValue
    protected abstract SnapshotHierarchy doUpdateVfsOnBuildStarted(SnapshotHierarchy root);
}
