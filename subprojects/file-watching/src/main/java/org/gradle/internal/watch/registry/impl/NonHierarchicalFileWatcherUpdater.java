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
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.watch.WatchingNotSupportedException;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NonHierarchicalFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonHierarchicalFileWatcherUpdater.class);

    private final Multiset<String> watchedRoots = HashMultiset.create();
    private final Map<String, ImmutableList<String>> watchedRootsForSnapshot = new HashMap<>();
    private final FileWatcher fileWatcher;

    public NonHierarchicalFileWatcherUpdater(FileWatcher fileWatcher) {
        this.fileWatcher = fileWatcher;
    }

    @Override
    public void changed(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots) {
        Map<String, Integer> changedWatchedDirectories = new HashMap<>();

        removedSnapshots.forEach(snapshot -> {
            ImmutableList<String> previousWatchedRoots = watchedRootsForSnapshot.remove(snapshot.getAbsolutePath());
            previousWatchedRoots.forEach(path -> decrement(path, changedWatchedDirectories));
            snapshot.accept(new OnlyVisitSubDirectories(path -> decrement(path, changedWatchedDirectories)));
        });
        addedSnapshots.forEach(snapshot -> {
            ImmutableList<String> directoriesToWatchForRoot = ImmutableList.copyOf(SnapshotWatchedDirectoryFinder.getDirectoriesToWatch(snapshot).stream()
                .map(Path::toString).collect(Collectors.toList()));
            watchedRootsForSnapshot.put(snapshot.getAbsolutePath(), directoriesToWatchForRoot);
            directoriesToWatchForRoot.forEach(path -> increment(path, changedWatchedDirectories));
            snapshot.accept(new OnlyVisitSubDirectories(path -> increment(path, changedWatchedDirectories)));
        });
        updateWatchedDirectories(changedWatchedDirectories);
    }

    @Override
    public void buildFinished() {
    }

    @Override
    public void updateRootProjectDirectories(Collection<File> updatedRootProjectDirectories) {
    }

    private void updateWatchedDirectories(Map<String, Integer> changedWatchDirectories) {
        if (changedWatchDirectories.isEmpty()) {
            return;
        }
        Set<File> watchRootsToRemove = new HashSet<>();
        Set<File> watchRootsToAdd = new HashSet<>();
        changedWatchDirectories.forEach((absolutePath, value) -> {
            int count = value;
            if (count < 0) {
                int toRemove = -count;
                int contained = watchedRoots.remove(absolutePath, toRemove);
                if (contained <= toRemove) {
                    watchRootsToRemove.add(new File(absolutePath));
                }
            } else if (count > 0) {
                int contained = watchedRoots.add(absolutePath, count);
                if (contained == 0) {
                    watchRootsToAdd.add(new File(absolutePath));
                }
            }
        });
        if (watchedRoots.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        LOGGER.info("Watching {} directories to track changes", watchedRoots.entrySet().size());
        try {
            if (!watchRootsToRemove.isEmpty()) {
                fileWatcher.stopWatching(watchRootsToRemove);
            }
            if (!watchRootsToAdd.isEmpty()) {
                fileWatcher.startWatching(watchRootsToAdd);
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

    private static class OnlyVisitSubDirectories implements FileSystemSnapshotVisitor {
        private final Consumer<String> subDirectoryConsumer;
        boolean root;

        public OnlyVisitSubDirectories(Consumer<String> subDirectoryConsumer) {
            this.subDirectoryConsumer = subDirectoryConsumer;
            this.root = true;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            boolean directoryAccessedDirectly = directorySnapshot.getAccessType() == AccessType.DIRECT;
            if (!root && directoryAccessedDirectly) {
                subDirectoryConsumer.accept(directorySnapshot.getAbsolutePath());
            }
            root = false;
            return directoryAccessedDirectly;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }
    }
}
