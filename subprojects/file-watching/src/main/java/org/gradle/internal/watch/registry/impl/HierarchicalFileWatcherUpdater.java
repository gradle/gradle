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
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Set<Path> watchedHierarchies = new HashSet<>();
    private final FileWatcher fileWatcher;

    private final FileSystemLocationToWatchValidator locationToWatchValidator;
    private final WatchableHierarchies watchableHierarchies;

    public HierarchicalFileWatcherUpdater(FileWatcher fileWatcher, FileSystemLocationToWatchValidator locationToWatchValidator, Predicate<String> watchFilter, int maxHierarchiesToWatch) {
        this.fileWatcher = fileWatcher;
        this.locationToWatchValidator = locationToWatchValidator;
        this.watchableHierarchies = new WatchableHierarchies(watchFilter, maxHierarchiesToWatch);
    }

    @Override
    public void virtualFileSystemContentsChanged(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        determineAndUpdateWatchedHierarchies(root);
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        determineAndUpdateWatchedHierarchies(root);
    }

    @Override
    public SnapshotHierarchy buildFinished(SnapshotHierarchy root) {
        Set<File> watchedFiles = watchedHierarchies.stream().map(Path::toFile).collect(Collectors.toSet());
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchedSnapshots(
            watchedFiles::contains,
            root,
            (location, currentRoot) -> currentRoot.invalidate(location, SnapshotHierarchy.NodeDiffListener.NOOP)
        );

        determineAndUpdateWatchedHierarchies(newRoot);
        LOGGER.info("Watched directory hierarchies: {}", watchedHierarchies);
        return newRoot;
    }

    @Override
    public int getNumberOfWatchedHierarchies() {
        return watchedHierarchies.size();
    }

    private void determineAndUpdateWatchedHierarchies(SnapshotHierarchy root) {
        Set<String> snapshotsAlreadyCoveredByOtherHierarchies = new HashSet<>();
        Set<Path> hierarchiesWithSnapshots = watchableHierarchies.getWatchableHierarchies().stream()
            .flatMap(watchableHierarchy -> {
                CheckIfNonEmptySnapshotVisitor checkIfNonEmptySnapshotVisitor = new CheckIfNonEmptySnapshotVisitor(watchableHierarchies);
                root.visitSnapshotRoots(watchableHierarchy.getAbsolutePath(), new FilterAlreadyCoveredSnapshotsVisitor(checkIfNonEmptySnapshotVisitor, snapshotsAlreadyCoveredByOtherHierarchies));
                if (checkIfNonEmptySnapshotVisitor.isEmpty()) {
                    return Stream.empty();
                }
                return checkIfNonEmptySnapshotVisitor.containsOnlyMissingFiles()
                    ? Stream.of(locationOrFirstExistingAncestor(watchableHierarchy.toPath()))
                    : Stream.of(watchableHierarchy.toPath());
            })
            .collect(Collectors.toSet());
        updateWatchedHierarchies(resolveHierarchiesToWatch(hierarchiesWithSnapshots));
    }

    private Path locationOrFirstExistingAncestor(Path watchableHierarchy) {
        if (Files.isDirectory(watchableHierarchy)) {
            return watchableHierarchy;
        }
        return SnapshotWatchedDirectoryFinder.findFirstExistingAncestor(watchableHierarchy);
    }

    private void updateWatchedHierarchies(Set<Path> newWatchedHierarchies) {
        if (newWatchedHierarchies.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        Set<Path> hierarchiesToStopWatching = new HashSet<>(watchedHierarchies);
        Set<Path> hierarchiesToStartWatching = new HashSet<>(newWatchedHierarchies);
        hierarchiesToStopWatching.removeAll(newWatchedHierarchies);
        hierarchiesToStartWatching.removeAll(watchedHierarchies);
        if (hierarchiesToStartWatching.isEmpty() && hierarchiesToStopWatching.isEmpty()) {
            return;
        }
        if (!hierarchiesToStopWatching.isEmpty()) {
            fileWatcher.stopWatching(hierarchiesToStopWatching.stream()
                .map(Path::toFile)
                .collect(Collectors.toList())
            );
            watchedHierarchies.removeAll(hierarchiesToStopWatching);
        }
        if (!hierarchiesToStartWatching.isEmpty()) {
            fileWatcher.startWatching(hierarchiesToStartWatching.stream()
                .map(Path::toFile)
                .peek(locationToWatchValidator::validateLocationToWatch)
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

    public interface FileSystemLocationToWatchValidator {
        FileSystemLocationToWatchValidator NO_VALIDATION = location -> {};

        void validateLocationToWatch(File location);
    }

    private static class FilterAlreadyCoveredSnapshotsVisitor implements SnapshotHierarchy.SnapshotVisitor {
        private final SnapshotHierarchy.SnapshotVisitor delegate;
        private final Set<String> alreadyCoveredSnapshots;

        public FilterAlreadyCoveredSnapshotsVisitor(SnapshotHierarchy.SnapshotVisitor delegate, Set<String> alreadyCoveredSnapshots) {
            this.delegate = delegate;
            this.alreadyCoveredSnapshots = alreadyCoveredSnapshots;
        }

        @Override
        public void visitSnapshotRoot(CompleteFileSystemLocationSnapshot snapshot) {
            if (alreadyCoveredSnapshots.add(snapshot.getAbsolutePath())) {
                delegate.visitSnapshotRoot(snapshot);
            }
        }
    }
}
