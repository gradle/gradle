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

import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Updater for hierarchical file watchers.
 *
 * For hierarchical watchers, we can use the registered watchable hierarchies as watched directories.
 * Build root directories are always watchable hierarchies.
 * Watching the build root directories is better since they are less likely to be deleted and
 * nearly no changes to the watched directories are necessary when running builds on the same project.
 *
 * To allow deleting the build root directories, we need to stop watching a build root directory if there are no more snapshots in the VFS inside,
 * since watched directories can't be deleted on Windows.
 *
 * The build root directories are discovered as included builds are encountered at the start of a build, and then they are removed when the build finishes.
 *
 * This is the lifecycle for the watchable hierarchies:
 * - During a build, there will be various calls to {@link FileWatcherUpdater#registerWatchableHierarchy(File, SnapshotHierarchy)},
 *   each call augmenting the collection. The watchers will be updated accordingly.
 * - When updating the watches, we watch watchable hierarchies registered for this build or old watched directories from previous builds instead of
 *   directories inside them.
 * - At the end of the build
 *   - stop watching the watchable directories with nothing to watch inside
 *   - remember the currently watched directories as old watched directories for the next build
 *   - remove everything that isn't watched from the virtual file system.
 */
public class HierarchicalFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(HierarchicalFileWatcherUpdater.class);

    private final FileWatcher fileWatcher;

    private final FileSystemLocationToWatchValidator locationToWatchValidator;
    private final WatchableHierarchies watchableHierarchies;
    private final WatchedHierarchies watchedHierarchies = new WatchedHierarchies();

    public HierarchicalFileWatcherUpdater(FileWatcher fileWatcher, FileSystemLocationToWatchValidator locationToWatchValidator, Predicate<String> watchFilter) {
        this.fileWatcher = fileWatcher;
        this.locationToWatchValidator = locationToWatchValidator;
        this.watchableHierarchies = new WatchableHierarchies(watchFilter);
    }

    @Override
    public void virtualFileSystemContentsChanged(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        boolean directoriesToWatchChanged = watchableHierarchies.getWatchableHierarchies().stream().anyMatch(watchableHierarchy -> {
            boolean hasSnapshotsToWatch = root.hasDescendantsUnder(watchableHierarchy.toString());
            if (watchedHierarchies.contains(watchableHierarchy)) {
                // Need to stop watching this hierarchy
                return !hasSnapshotsToWatch;
            } else {
                // Need to start watching this hierarchy
                return hasSnapshotsToWatch;
            }
        });
        if (directoriesToWatchChanged) {
            updateWatchedHierarchies(root);
        }
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        updateWatchedHierarchies(root);
    }

    @Override
    public SnapshotHierarchy buildFinished(SnapshotHierarchy root, int maximumNumberOfWatchedHierarchies) {
        WatchableHierarchies.Invalidator invalidator = (location, currentRoot) -> currentRoot.invalidate(location, SnapshotHierarchy.NodeDiffListener.NOOP);
        SnapshotHierarchy newRoot = watchableHierarchies.removeWatchedHierarchiesOverLimit(
            root,
            watchedHierarchies::contains,
            maximumNumberOfWatchedHierarchies,
            invalidator
        );
        newRoot = watchableHierarchies.removeUnwatchedSnapshots(
            newRoot,
            invalidator
        );

        updateWatchedHierarchies(newRoot);
        LOGGER.info("Watched directory hierarchies: {}", watchedHierarchies.getWatchedRoots());
        return newRoot;
    }

    @Override
    public int getNumberOfWatchedHierarchies() {
        return watchedHierarchies.getWatchedRoots().size();
    }

    private void updateWatchedHierarchies(SnapshotHierarchy root) {
        Set<Path> oldWatchedRoots = watchedHierarchies.getWatchedRoots();
        watchedHierarchies.updateWatchedHierarchies(watchableHierarchies, root);
        Set<Path> newWatchedRoots = watchedHierarchies.getWatchedRoots();

        if (newWatchedRoots.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        Set<Path> hierarchiesToStopWatching = new HashSet<>(oldWatchedRoots);
        Set<Path> hierarchiesToStartWatching = new HashSet<>(newWatchedRoots);
        hierarchiesToStopWatching.removeAll(newWatchedRoots);
        hierarchiesToStartWatching.removeAll(oldWatchedRoots);
        if (hierarchiesToStartWatching.isEmpty() && hierarchiesToStopWatching.isEmpty()) {
            return;
        }
        if (!hierarchiesToStopWatching.isEmpty()) {
            fileWatcher.stopWatching(hierarchiesToStopWatching.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
        }
        if (!hierarchiesToStartWatching.isEmpty()) {
            fileWatcher.startWatching(hierarchiesToStartWatching.stream()
                .map(Path::toFile)
                .peek(locationToWatchValidator::validateLocationToWatch)
                .collect(Collectors.toList())
            );
        }
        LOGGER.info("Watching {} directory hierarchies to track changes", newWatchedRoots.size());
    }

    public interface FileSystemLocationToWatchValidator {
        FileSystemLocationToWatchValidator NO_VALIDATION = location -> {};

        void validateLocationToWatch(File location);
    }
}
