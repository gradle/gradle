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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Updater for hierarchical file watchers.
 *
 * This is the lifecycle for the watched project root directories:
 * - During a build, there will be various calls to {@link #updateProjectRootDirectories(Collection)},
 *   each call augmenting the collection. The watchers will be updated accordingly.
 * - We try not to stop watching project root directories during a build.
 * - At the end of the build
 *   - stop watching the project root directories with nothing to watch inside
 *   - remember the current watched project root directories as old root directories for the next build
 *   - remove all non-watched project root directories from the old root directories.
 * - When updating the watches, we watch project root directories or old project root directories instead of
 *   directories inside them.
 *
 * The goal of the above logic is to
 * - Keep the updates to the watched directories during a build to a minimum.
 * - Release the watch on a project directory if it is deleted while the daemon is idle.
 */
public class HierarchicalFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(HierarchicalFileWatcherUpdater.class);

    private final Map<String, ImmutableList<Path>> watchedRootsForSnapshot = new HashMap<>();
    private final Multiset<Path> shouldWatchDirectories = HashMultiset.create();
    private final Set<Path> watchedRoots = new HashSet<>();
    private final Map<Path, String> projectRootDirectories = new HashMap<>();
    private final Map<Path, String> oldProjectRootDirectories = new HashMap<>();
    private final Set<Path> recentlyWatchedProjectRootDirectories = new HashSet<>();
    private final FileWatcher watcher;

    public HierarchicalFileWatcherUpdater(FileWatcher watcher) {
        this.watcher = watcher;
    }

    @Override
    public void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots) {
        removedSnapshots.forEach(snapshot -> {
            ImmutableList<Path> previouslyWatchedRootsForSnapshot = watchedRootsForSnapshot.remove(snapshot.getAbsolutePath());
            Multisets.removeOccurrences(shouldWatchDirectories, previouslyWatchedRootsForSnapshot);
        });
        addedSnapshots.forEach(snapshot -> {
            ImmutableList<Path> directoriesToWatch = WatchRootUtil.getDirectoriesToWatch(snapshot);
            shouldWatchDirectories.addAll(directoriesToWatch);
            watchedRootsForSnapshot.put(snapshot.getAbsolutePath(), directoriesToWatch);
        });
        updateWatchedDirectories();
    }

    @Override
    public void buildFinished() {
        recentlyWatchedProjectRootDirectories.clear();
        oldProjectRootDirectories.putAll(projectRootDirectories);
        projectRootDirectories.clear();
        updateWatchedDirectories();
        oldProjectRootDirectories.keySet().retainAll(watchedRoots);
    }

    @Override
    public void updateProjectRootDirectories(Collection<File> updatedProjectRootDirectories) {
        Set<Path> rootPaths = updatedProjectRootDirectories.stream()
            .map(File::toPath)
            .map(Path::toAbsolutePath)
            .collect(Collectors.toSet());
        Set<Path> newProjectRootDirectories = WatchRootUtil.resolveRootsToWatch(rootPaths);
        LOGGER.info("Now considering {} as root directories to watch", newProjectRootDirectories);

        oldProjectRootDirectories.keySet().removeAll(newProjectRootDirectories);

        projectRootDirectories.clear();
        newProjectRootDirectories.forEach(
            oldProjectRootDirectory -> projectRootDirectories.put(oldProjectRootDirectory, oldProjectRootDirectory.toString() + File.separator)
        );

        updateWatchedDirectories();
    }

    private void updateWatchedDirectories() {
        Set<Path> directoriesToWatch = new HashSet<>();
        shouldWatchDirectories.elementSet().forEach(shouldWatchDirectory -> {
            String shouldWatchDirectoryPathString = shouldWatchDirectory.toString();
            if (maybeWatchProjectRootDirectory(directoriesToWatch, shouldWatchDirectoryPathString, projectRootDirectories)) {
                return;
            }
            if (maybeWatchProjectRootDirectory(directoriesToWatch, shouldWatchDirectoryPathString, oldProjectRootDirectories)) {
                return;
            }
            directoriesToWatch.add(shouldWatchDirectory);
        });
        directoriesToWatch.addAll(recentlyWatchedProjectRootDirectories);

        updateWatchedDirectories(WatchRootUtil.resolveRootsToWatch(directoriesToWatch));
    }

    private static boolean maybeWatchProjectRootDirectory(Set<Path> directoriesToWatch, String shouldWatchDirectoryPathString, Map<Path, String> projectRootDirectories) {
        for (Map.Entry<Path, String> entry : projectRootDirectories.entrySet()) {
            Path projectRootDirectory = entry.getKey();
            String projectRootDirectoryPrefix = entry.getValue();
            if (shouldWatchDirectoryPathString.startsWith(projectRootDirectoryPrefix)) {
                directoriesToWatch.add(projectRootDirectory);
                return true;
            }
        }
        return false;
    }

    private void updateWatchedDirectories(Set<Path> newWatchRoots) {
        Set<Path> watchRootsToRemove = new HashSet<>(watchedRoots);
        if (newWatchRoots.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        watchRootsToRemove.removeAll(newWatchRoots);
        newWatchRoots.removeAll(watchedRoots);
        if (newWatchRoots.isEmpty() && watchRootsToRemove.isEmpty()) {
            return;
        }
        if (!watchRootsToRemove.isEmpty()) {
            watcher.stopWatching(watchRootsToRemove.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
            watchedRoots.removeAll(watchRootsToRemove);
        }
        if (!newWatchRoots.isEmpty()) {
            watcher.startWatching(newWatchRoots.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
            watchedRoots.addAll(newWatchRoots);
            newWatchRoots.stream()
                .filter(projectRootDirectories::containsKey)
                .forEach(recentlyWatchedProjectRootDirectories::add);
        }
        LOGGER.info("Watching {} directory hierarchies to track changes", watchedRoots.size());
    }
}
