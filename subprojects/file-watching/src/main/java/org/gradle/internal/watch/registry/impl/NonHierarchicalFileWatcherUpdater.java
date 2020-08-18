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
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.registry.SnapshotCollectingDiffListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NonHierarchicalFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonHierarchicalFileWatcherUpdater.class);

    private final Multiset<String> watchedDirectories = HashMultiset.create();
    private final Map<String, ImmutableList<String>> watchedDirectoriesForSnapshot = new HashMap<>();
    private final FileWatcher fileWatcher;

    private final WatchableHierarchies watchableHierarchies;

    public NonHierarchicalFileWatcherUpdater(FileWatcher fileWatcher, Predicate<String> watchFilter) {
        this.fileWatcher = fileWatcher;
        this.watchableHierarchies = new WatchableHierarchies(watchFilter);
    }

    @Override
    public void virtualFileSystemContentsChanged(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        Map<String, Integer> changedWatchedDirectories = new HashMap<>();

        removedSnapshots.stream()
            .filter(watchableHierarchies::shouldWatch)
            .forEach(snapshot -> {
                ImmutableList<String> previousWatchedRoots = watchedDirectoriesForSnapshot.remove(snapshot.getAbsolutePath());
                previousWatchedRoots.forEach(path -> decrement(path, changedWatchedDirectories));
                snapshot.accept(new SubdirectoriesToWatchVisitor(path -> decrement(path, changedWatchedDirectories)));
            });
        addedSnapshots.stream()
            .filter(watchableHierarchies::shouldWatch)
            .forEach(snapshot -> {
                ImmutableList<String> directoriesToWatchForRoot = ImmutableList.copyOf(SnapshotWatchedDirectoryFinder.getDirectoriesToWatch(snapshot).stream()
                    .map(Path::toString).collect(Collectors.toList()));
                watchedDirectoriesForSnapshot.put(snapshot.getAbsolutePath(), directoriesToWatchForRoot);
                directoriesToWatchForRoot.forEach(path -> increment(path, changedWatchedDirectories));
                snapshot.accept(new SubdirectoriesToWatchVisitor(path -> increment(path, changedWatchedDirectories)));
            });
        updateWatchedDirectories(changedWatchedDirectories);
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
    }

    @Override
    public SnapshotHierarchy buildFinished(SnapshotHierarchy root, int maximumNumberOfWatchedHierarchies) {
        WatchableHierarchies.Invalidator invalidator = (location, currentRoot) -> {
            SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener();
            SnapshotHierarchy invalidatedRoot = currentRoot.invalidate(location, diffListener);
            diffListener.publishSnapshotDiff((removedSnapshots, addedSnapshots) -> virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, invalidatedRoot));
            return invalidatedRoot;
        };
        SnapshotHierarchy newRoot = watchableHierarchies.removeWatchedHierarchiesOverLimit(
            root,
            hierarchy -> containsSnapshots(hierarchy, root),
            maximumNumberOfWatchedHierarchies,
            invalidator
        );
        newRoot = watchableHierarchies.removeUnwatchedSnapshots(
            newRoot,
            invalidator
        );
        LOGGER.info("Watching {} directories to track changes", watchedDirectories.entrySet().size());
        return newRoot;
    }

    @Override
    public int getNumberOfWatchedHierarchies() {
        return watchableHierarchies.getWatchableHierarchies().size();
    }

    private boolean containsSnapshots(Path location, SnapshotHierarchy root) {
        CheckIfNonEmptySnapshotVisitor checkIfNonEmptySnapshotVisitor = new CheckIfNonEmptySnapshotVisitor(watchableHierarchies);
        root.visitSnapshotRoots(location.toString(), checkIfNonEmptySnapshotVisitor);
        return !checkIfNonEmptySnapshotVisitor.isEmpty();
    }

    private void updateWatchedDirectories(Map<String, Integer> changedWatchDirectories) {
        if (changedWatchDirectories.isEmpty()) {
            return;
        }
        Set<File> directoriesToStopWatching = new HashSet<>();
        Set<File> directoriesToStartWatching = new HashSet<>();
        changedWatchDirectories.forEach((absolutePath, value) -> {
            int count = value;
            if (count < 0) {
                int toRemove = -count;
                int contained = watchedDirectories.remove(absolutePath, toRemove);
                if (contained <= toRemove) {
                    directoriesToStopWatching.add(new File(absolutePath));
                }
            } else if (count > 0) {
                int contained = watchedDirectories.add(absolutePath, count);
                if (contained == 0) {
                    directoriesToStartWatching.add(new File(absolutePath));
                }
            }
        });
        if (watchedDirectories.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        LOGGER.info("Watching {} directories to track changes", watchedDirectories.entrySet().size());
        try {
            if (!directoriesToStopWatching.isEmpty()) {
                fileWatcher.stopWatching(directoriesToStopWatching);
            }
            if (!directoriesToStartWatching.isEmpty()) {
                fileWatcher.startWatching(directoriesToStartWatching);
            }
        } catch (NativeException e) {
            if (e.getMessage().contains("Already watching path: ")) {
                throw new WatchingNotSupportedException("Unable to watch same file twice via different paths: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private static void decrement(String path, Map<String, Integer> changedWatchedDirectories) {
        changedWatchedDirectories.compute(path, (key, value) -> value == null ? -1 : value - 1);
    }

    private static void increment(String path, Map<String, Integer> changedWatchedDirectories) {
        changedWatchedDirectories.compute(path, (key, value) -> value == null ? 1 : value + 1);
    }

    private class SubdirectoriesToWatchVisitor implements FileSystemSnapshotVisitor {
        private final Consumer<String> subDirectoryToWatchConsumer;
        private boolean root;

        public SubdirectoriesToWatchVisitor(Consumer<String> subDirectoryToWatchConsumer) {
            this.subDirectoryToWatchConsumer = subDirectoryToWatchConsumer;
            this.root = true;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            if (root) {
                root = false;
                return true;
            }
            if (watchableHierarchies.ignoredForWatching(directorySnapshot)) {
                return false;
            } else {
                subDirectoryToWatchConsumer.accept(directorySnapshot.getAbsolutePath());
                return true;
            }
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }
    }
}
