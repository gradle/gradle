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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotTransformer;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class NonHierarchicalFileWatcherUpdater extends AbstractFileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonHierarchicalFileWatcherUpdater.class);

    private final FileWatcher fileWatcher;
    private final Multiset<String> watchedDirectories = HashMultiset.create();
    private final Map<String, String> watchedDirectoryForSnapshot = new HashMap<>();

    public NonHierarchicalFileWatcherUpdater(
        FileWatcher fileWatcher,
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies
    ) {
        super(probeRegistry, watchableHierarchies);
        this.fileWatcher = fileWatcher;
    }

    @Override
    protected boolean handleVirtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        Map<String, Integer> changedWatchedDirectories = new HashMap<>();

        removedSnapshots.stream()
            .filter(watchableHierarchies::shouldWatch)
            .forEach(snapshot -> {
                String previousWatchedRoot = watchedDirectoryForSnapshot.remove(snapshot.getAbsolutePath());
                decrement(previousWatchedRoot, changedWatchedDirectories);
                snapshot.accept(new SubdirectoriesToWatchVisitor(path -> decrement(path, changedWatchedDirectories)));
            });
        addedSnapshots.stream()
            .filter(watchableHierarchies::shouldWatch)
            .forEach(snapshot -> {
                File directoryToWatchForRoot = SnapshotWatchedDirectoryFinder.getDirectoryToWatch(snapshot);
                String pathToWatchForRoot = directoryToWatchForRoot.getAbsolutePath();
                if (!watchableHierarchies.isInWatchableHierarchy(pathToWatchForRoot)) {
                    return;
                }
                watchedDirectoryForSnapshot.put(snapshot.getAbsolutePath(), pathToWatchForRoot);
                increment(pathToWatchForRoot, changedWatchedDirectories);
                snapshot.accept(new SubdirectoriesToWatchVisitor(path -> increment(path, changedWatchedDirectories)));
            });
        if (changedWatchedDirectories.isEmpty()) {
            return false;
        }
        updateWatchedDirectories(changedWatchedDirectories);
        return true;
    }

    @Override
    protected SnapshotHierarchy doUpdateVfsOnBuildStarted(SnapshotHierarchy root) {
        return root;
    }

    @Override
    protected void updateWatchesOnChangedWatchedFiles(FileHierarchySet watchedFiles) {
        // The changes already happened in `handleVirtualFileSystemContentsChanged`.
    }

    @Override
    protected WatchableHierarchies.Invalidator createInvalidator() {
        return (location, currentRoot) -> {
            SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener();
            SnapshotHierarchy invalidatedRoot = currentRoot.invalidate(location, diffListener);
            diffListener.publishSnapshotDiff((removedSnapshots, addedSnapshots) -> virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, invalidatedRoot));
            return invalidatedRoot;
        };
    }

    @Override
    protected void startWatchingProbeDirectory(File probeDirectory) {
        updateWatchedDirectories(ImmutableMap.of(probeDirectory.getAbsolutePath(), 1));
    }

    @Override
    protected void stopWatchingProbeDirectory(File probeDirectory) {
        updateWatchedDirectories(ImmutableMap.of(probeDirectory.getAbsolutePath(), -1));
    }

    private void updateWatchedDirectories(Map<String, Integer> changedWatchDirectories) {
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

        LOGGER.info("Watching {} directories to track changes", watchedDirectories.entrySet().size());

        try {
            if (!directoriesToStopWatching.isEmpty()) {
                if (!fileWatcher.stopWatching(directoriesToStopWatching)) {
                    LOGGER.debug("Couldn't stop watching directories: {}", directoriesToStopWatching);
                }
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

    private class SubdirectoriesToWatchVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
        private final Consumer<String> subDirectoryToWatchConsumer;

        public SubdirectoriesToWatchVisitor(Consumer<String> subDirectoryToWatchConsumer) {
            this.subDirectoryToWatchConsumer = subDirectoryToWatchConsumer;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
            if (isRoot) {
                return SnapshotVisitResult.CONTINUE;
            }
            return snapshot.accept(new FileSystemLocationSnapshotTransformer<SnapshotVisitResult>() {
                @Override
                public SnapshotVisitResult visitDirectory(DirectorySnapshot directorySnapshot) {
                    if (watchableHierarchies.ignoredForWatching(directorySnapshot)) {
                        return SnapshotVisitResult.SKIP_SUBTREE;
                    } else {
                        subDirectoryToWatchConsumer.accept(directorySnapshot.getAbsolutePath());
                        return SnapshotVisitResult.CONTINUE;
                    }
                }

                @Override
                public SnapshotVisitResult visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    return SnapshotVisitResult.CONTINUE;
                }

                @Override
                public SnapshotVisitResult visitMissing(MissingFileSnapshot missingSnapshot) {
                    return SnapshotVisitResult.CONTINUE;
                }
            });
        }
    }
}
