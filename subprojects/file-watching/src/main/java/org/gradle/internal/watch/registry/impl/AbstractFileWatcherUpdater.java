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

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.vfs.WatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileWatcherUpdater.class);

    private final FileSystemLocationToWatchValidator locationToWatchValidator;
    private final FileWatcherProbeRegistry probeRegistry;
    protected final WatchableHierarchies watchableHierarchies;
    protected WatchedHierarchies watchedHierarchies = WatchedHierarchies.EMPTY;

    public AbstractFileWatcherUpdater(
        FileSystemLocationToWatchValidator locationToWatchValidator,
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies
    ) {
        this.locationToWatchValidator = locationToWatchValidator;
        this.probeRegistry = probeRegistry;
        this.watchableHierarchies = watchableHierarchies;
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, File probeFile, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        probeRegistry.registerProbe(watchableHierarchy, probeFile);
        updateWatchedHierarchies(root);
    }

    @Override
    public final SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode) {
        watchableHierarchies.updateUnsupportedFileSystems(watchMode);
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildStart(root, createInvalidator());
        newRoot = doUpdateVfsOnBuildStarted(newRoot);
        if (root != newRoot) {
            updateWatchedHierarchies(newRoot);
        }
        return newRoot;
    }

    @CheckReturnValue
    protected abstract SnapshotHierarchy doUpdateVfsOnBuildStarted(SnapshotHierarchy root);

    @Override
    public void virtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        boolean contentsChanged = handleVirtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, root);
        if (contentsChanged) {
            updateWatchedHierarchies(root);
        }
    }

    protected abstract boolean handleVirtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root);

    @Override
    public SnapshotHierarchy updateVfsOnBuildFinished(SnapshotHierarchy root, WatchMode watchMode, int maximumNumberOfWatchedHierarchies) {
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildFinished(
            root,
            watchMode,
            watchedHierarchies::contains,
            maximumNumberOfWatchedHierarchies,
            createInvalidator()
        );

        if (root != newRoot) {
            updateWatchedHierarchies(newRoot);
        }
        LOGGER.info("Watched directory hierarchies: {}", watchedHierarchies.getWatchedRoots());
        return newRoot;
    }

    @Override
    public Collection<File> getWatchedRoots() {
        return watchedHierarchies.getWatchedRoots();
    }

    @Override
    public void triggerWatchProbe(String path) {
        probeRegistry.triggerWatchProbe(path);
    }

    protected abstract WatchableHierarchies.Invalidator createInvalidator();

    private void updateWatchedHierarchies(SnapshotHierarchy root) {
        Set<File> oldWatchedRoots = watchedHierarchies.getWatchedRoots();
        watchedHierarchies = WatchedHierarchies.resolveWatchedHierarchies(watchableHierarchies, root);
        Set<File> newWatchedRoots = watchedHierarchies.getWatchedRoots();

        if (newWatchedRoots.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        Set<File> hierarchiesToStopWatching = new HashSet<>(oldWatchedRoots);
        Set<File> hierarchiesToStartWatching = new HashSet<>(newWatchedRoots);
        hierarchiesToStopWatching.removeAll(newWatchedRoots);
        hierarchiesToStartWatching.removeAll(oldWatchedRoots);
        if (hierarchiesToStartWatching.isEmpty() && hierarchiesToStopWatching.isEmpty()) {
            return;
        }
        if (!hierarchiesToStopWatching.isEmpty()) {
            hierarchiesToStopWatching.forEach(probeRegistry::disarmWatchProbe);
            stopWatchingHierarchies(hierarchiesToStopWatching);
        }
        if (!hierarchiesToStartWatching.isEmpty()) {
            hierarchiesToStartWatching.forEach(locationToWatchValidator::validateLocationToWatch);
            startWatchingHierarchies(hierarchiesToStartWatching);
            hierarchiesToStartWatching.forEach(probeRegistry::armWatchProbe);
        }
        LOGGER.info("Watching {} directory hierarchies to track changes", newWatchedRoots.size());
    }

    protected abstract void startWatchingHierarchies(Collection<File> hierarchiesToWatch);
    protected abstract void stopWatchingHierarchies(Collection<File> hierarchiesToWatch);

    public interface FileSystemLocationToWatchValidator {
        FileSystemLocationToWatchValidator NO_VALIDATION = location -> {};

        void validateLocationToWatch(File location);
    }
}
