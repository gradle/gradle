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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Updater for hierarchical file watchers.
 *
 * We want to keep track of root project directories for hierarchical watchers,
 * because we prefer watching the root project directory instead of directories inside.
 * Watching the root project directories is better since they are less likely to be deleted and
 * nearly no changes to the watched directories are necessary when running builds on the same project.
 *
 * To allow deleting the root project directories, we need to stop watching a root project directory if there are no more snapshots in the VFS inside,
 * since watched directories can't be deleted on Windows.
 *
 * The root project directories are discovered as included builds are encountered at the start of a build, and then they are removed when the build finishes.
 *
 * This is the lifecycle for the watched root project directories:
 * - During a build, there will be various calls to {@link #updateRootProjectDirectories(Collection)},
 *   each call augmenting the collection. The watchers will be updated accordingly.
 * - When updating the watches, we watch root project directories or old root project directories instead of
 *   directories inside them.
 * - At the end of the build
 *   - stop watching the root project directories with nothing to watch inside
 *   - remember the current watched root project directories as old root directories for the next build
 *   - remove all non-watched root project directories from the old root directories.
 */
public class HierarchicalFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(HierarchicalFileWatcherUpdater.class);

    private final Multimap<String, Path> trackedDirectoriesForSnapshot = HashMultimap.create();

    private final Set<Path> watchedHierarchies = new HashSet<>();

    private final Set<Path> knownRootProjectDirectoriesFromCurrentBuild = new HashSet<>();
    private final Set<Path> watchedRootProjectDirectoriesFromPreviousBuild = new HashSet<>();

    private final FileWatcher watcher;

    public HierarchicalFileWatcherUpdater(FileWatcher watcher) {
        this.watcher = watcher;
    }

    @Override
    public void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots) {
        removedSnapshots.forEach(snapshot -> trackedDirectoriesForSnapshot.removeAll(snapshot.getAbsolutePath()));
        addedSnapshots.forEach(snapshot -> {
            ImmutableList<Path> directoriesToWatch = SnapshotWatchedDirectoryFinder.getDirectoriesToWatch(snapshot);
            trackedDirectoriesForSnapshot.putAll(snapshot.getAbsolutePath(), directoriesToWatch);
        });
        determineAndUpdateWatchedHierarchies();
    }

    @Override
    public void buildFinished() {
        watchedRootProjectDirectoriesFromPreviousBuild.addAll(knownRootProjectDirectoriesFromCurrentBuild);
        watchedRootProjectDirectoriesFromPreviousBuild.retainAll(watchedHierarchies);
        knownRootProjectDirectoriesFromCurrentBuild.clear();
        determineAndUpdateWatchedHierarchies();
    }

    @Override
    public void updateRootProjectDirectories(Collection<File> updatedRootProjectDirectories) {
        Set<Path> rootPaths = updatedRootProjectDirectories.stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .collect(Collectors.toSet());
        Set<Path> newRootProjectDirectories = resolveHierarchiesToWatch(rootPaths);
        LOGGER.info("Now considering watching {} as root project directories", newRootProjectDirectories);

        knownRootProjectDirectoriesFromCurrentBuild.clear();
        knownRootProjectDirectoriesFromCurrentBuild.addAll(newRootProjectDirectories);
        watchedRootProjectDirectoriesFromPreviousBuild.removeAll(knownRootProjectDirectoriesFromCurrentBuild);

        determineAndUpdateWatchedHierarchies();
    }

    private void determineAndUpdateWatchedHierarchies() {
        Set<Path> hierarchiesToWatch = determineHierarchiesToWatch();
        updateWatchedHierarchies(hierarchiesToWatch);
    }

    private Set<Path> determineHierarchiesToWatch() {
        Set<Path> directoriesToWatch = trackedDirectoriesForSnapshot.values().stream()
            .map(trackedDirectory -> Stream.concat(knownRootProjectDirectoriesFromCurrentBuild.stream(), watchedRootProjectDirectoriesFromPreviousBuild.stream())
                .filter(trackedDirectory::startsWith)
                .findFirst()
                .orElse(trackedDirectory))
            .collect(Collectors.toSet());

        return resolveHierarchiesToWatch(directoriesToWatch);
    }

    private void updateWatchedHierarchies(Set<Path> newHierarchiesToWatch) {
        if (newHierarchiesToWatch.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        Set<Path> hierarchiesToStopWatching = new HashSet<>(watchedHierarchies);
        Set<Path> hierarchiesToStartWatching = new HashSet<>(newHierarchiesToWatch);
        hierarchiesToStopWatching.removeAll(newHierarchiesToWatch);
        hierarchiesToStartWatching.removeAll(watchedHierarchies);
        if (hierarchiesToStartWatching.isEmpty() && hierarchiesToStopWatching.isEmpty()) {
            return;
        }
        if (!hierarchiesToStopWatching.isEmpty()) {
            watcher.stopWatching(hierarchiesToStopWatching.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
            watchedHierarchies.removeAll(hierarchiesToStopWatching);
        }
        if (!hierarchiesToStartWatching.isEmpty()) {
            watcher.startWatching(hierarchiesToStartWatching.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
            watchedHierarchies.addAll(hierarchiesToStartWatching);
        }
        LOGGER.info("Watching {} directory hierarchies to track changes", watchedHierarchies.size());
    }

    /**
     * Filters out directories whose ancestor is also among the watched directories.
     */
    @VisibleForTesting
    static Set<Path> resolveHierarchiesToWatch(Set<Path> directories) {
        Set<Path> hierarchies = new HashSet<>();
        directories.stream()
            .sorted(Comparator.comparingInt(Path::getNameCount))
            .filter(path -> {
                Path parent = path;
                while (true) {
                    parent = parent.getParent();
                    if (parent == null) {
                        break;
                    }
                    if (hierarchies.contains(parent)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(hierarchies::add);
        return hierarchies;
    }
}
