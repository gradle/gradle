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

import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.watch.registry.WatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.gradle.internal.Combiners.nonCombining;

public class WatchableHierarchies {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchableHierarchies.class);

    public static final String INVALIDATING_HIERARCHY_MESSAGE = "Invalidating hierarchy because watch probe hasn't been triggered";

    private final Predicate<String> immutableLocationsFilter;

    /**
     * Files that can be watched.
     *
     * Those are normally project root directories from the current builds and from previous builds.
     */
    private FileHierarchySet watchableFiles = FileHierarchySet.empty();

    /**
     * Files in locations that do not support watching.
     *
     * Those are the mount points of file systems that do not support watching.
     */
    private FileHierarchySet unwatchableFiles = FileHierarchySet.empty();

    /**
     * Hierarchies in usage order, most recent first.
     */
    private final Deque<File> hierarchies = new ArrayDeque<>();
    private final List<File> watchableHierarchiesSinceLastBuildFinish = new ArrayList<>();

    public WatchableHierarchies(
        Predicate<String> immutableLocationsFilter
    ) {
        this.immutableLocationsFilter = immutableLocationsFilter;
    }

    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        String watchableHierarchyPath = watchableHierarchy.getAbsolutePath();
        if (immutableLocationsFilter.test(watchableHierarchyPath)) {
            throw new IllegalStateException(String.format(
                "Unable to watch directory '%s' since it is within Gradle's caches",
                watchableHierarchyPath
            ));
        }
        watchableHierarchiesSinceLastBuildFinish.add(watchableHierarchy);
        if (unwatchableFiles.contains(watchableHierarchyPath)) {
            LOGGER.info("Not watching {} since the file system is not supported", watchableHierarchy);
            return;
        }
        doRegisterWatchableHierarchy(watchableHierarchy, root);
    }

    private void doRegisterWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        String watchableHierarchyPath = watchableHierarchy.getAbsolutePath();
        if (!watchableFiles.contains(watchableHierarchyPath)) {
            checkThatNothingExistsInNewWatchableHierarchy(watchableHierarchyPath, root);
            hierarchies.addFirst(watchableHierarchy);
            watchableFiles = watchableFiles.plus(watchableHierarchy);
        } else {
            hierarchies.remove(watchableHierarchy);
            hierarchies.addFirst(watchableHierarchy);
        }
        LOGGER.info("Now considering {} as hierarchies to watch", hierarchies);
    }

    @CheckReturnValue
    public SnapshotHierarchy removeUnwatchableContentBeforeBuildFinished(
        SnapshotHierarchy root,
        Predicate<File> isWatchedHierarchy,
        int maximumNumberOfWatchedHierarchies,
        List<File> unsupportedFileSystems,
        Invalidator invalidator
    ) {
        SnapshotHierarchy newRoot = root;
        newRoot = removeWatchedHierarchiesOverLimit(newRoot, isWatchedHierarchy, maximumNumberOfWatchedHierarchies, invalidator);
        newRoot = removeUnwatchableFileSystems(newRoot, unsupportedFileSystems, invalidator);
        newRoot = removeUnwatchedSnapshots(newRoot, invalidator);
        watchableHierarchiesSinceLastBuildFinish.clear();
        return newRoot;
    }

    private SnapshotHierarchy removeUnwatchedSnapshots(SnapshotHierarchy newRoot, Invalidator invalidator) {
        // Keep only snapshots we can trust
        return retainOnlyMatchingSnapshots(newRoot, invalidator, snapshot -> {
            // Keep everything in immutable locations, because Gradle makes sure
            // to update the VFS when it updates those locations
            if (immutableLocationsFilter.test(snapshot.getAbsolutePath())) {
                return true;
            }

            // If it's not being watched, discard it, as we can't trust the VFS for these
            return isInWatchableHierarchy(snapshot.getAbsolutePath());
        });
    }

    @CheckReturnValue
    public static SnapshotHierarchy removeUnwatchableContentAfterBuildFinished(SnapshotHierarchy root, Invalidator invalidator) {
        SnapshotHierarchy newRoot = root;
        // We are not being notified about changes to content accessed via symlinks
        newRoot = removeIndirectlySymlinkedRoots(newRoot, invalidator);
        newRoot = removeDirectSymlinks(newRoot, invalidator);
        return newRoot;
    }

    @CheckReturnValue
    private static SnapshotHierarchy removeIndirectlySymlinkedRoots(SnapshotHierarchy root, Invalidator invalidator) {
        Map<String, Boolean> symlinkCache = new HashMap<>();
        return root.rootSnapshots()
            .filter(snapshot -> isAncestorASymlink(symlinkCache, new File(snapshot.getAbsolutePath())))
            .reduce(
                root,
                (oldRoot, snapshot) -> invalidator.invalidate(snapshot.getAbsolutePath(), oldRoot),
                nonCombining());
    }

    @CheckReturnValue
    private static SnapshotHierarchy removeDirectSymlinks(SnapshotHierarchy root, Invalidator invalidator) {
        return retainOnlyMatchingSnapshots(root, invalidator,
            snapshot -> snapshot.getAccessType() != FileMetadata.AccessType.VIA_SYMLINK);
    }

    private static boolean isAncestorASymlink(Map<String, Boolean> symlinkCache, File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return false;
        }
        if (isAncestorASymlink(symlinkCache, parent)) {
            return true;
        }
        return symlinkCache.computeIfAbsent(parent.getAbsolutePath(), __ -> Files.isSymbolicLink(parent.toPath()));
    }

    @CheckReturnValue
    private SnapshotHierarchy removeWatchedHierarchiesOverLimit(SnapshotHierarchy root, Predicate<File> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
        hierarchies.removeIf(hierarchy -> !isWatchedHierarchy.test(hierarchy));
        SnapshotHierarchy result = root;
        int toRemove = hierarchies.size() - maximumNumberOfWatchedHierarchies;
        if (toRemove > 0) {
            LOGGER.info(
                "Watching too many directories in the file system (watching {}, limit {}), dropping some state from the virtual file system",
                hierarchies.size(),
                maximumNumberOfWatchedHierarchies
            );
            for (int i = 0; i < toRemove; i++) {
                File locationToRemove = hierarchies.removeLast();
                result = invalidator.invalidate(locationToRemove.toString(), result);
            }
        }
        watchableFiles = buildWatchableFilesFromHierarchies(hierarchies);
        return result;
    }

    private static FileHierarchySet buildWatchableFilesFromHierarchies(Collection<File> hierarchies) {
        return hierarchies.stream()
            .reduce(FileHierarchySet.empty(), FileHierarchySet::plus, nonCombining());
    }

    @CheckReturnValue
    private static SnapshotHierarchy removeUnwatchableFileSystems(SnapshotHierarchy root, List<File> unsupportedFileSystems, Invalidator invalidator) {
        if (unsupportedFileSystems.isEmpty()) {
            return root;
        }

        SnapshotHierarchy invalidatedRoot = unsupportedFileSystems.stream()
            .reduce(
                root,
                (updatedRoot, fileSystem) -> invalidator.invalidate(fileSystem.getAbsolutePath(), updatedRoot),
                nonCombining()
            );
        if (invalidatedRoot != root) {
            LOGGER.info("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching. " +
                "The relevant state was discarded to ensure changes to these locations are properly detected. " +
                "You can override this by explicitly enabling file system watching.");
        }
        return invalidatedRoot;
    }

    @CheckReturnValue
    public SnapshotHierarchy removeUnwatchableContentOnBuildStart(SnapshotHierarchy root, Invalidator invalidator, WatchMode watchMode, List<File> unsupportedFileSystems, Stream<File> unprovenHierarchies) {
        SnapshotHierarchy newRoot = root;
        newRoot = removeUnprovenHierarchies(newRoot, invalidator, watchMode, unprovenHierarchies);
        newRoot = updateUnwatchableFilesOnBuildStart(newRoot, invalidator, unsupportedFileSystems);
        return newRoot;
    }

    @CheckReturnValue
    private SnapshotHierarchy removeUnprovenHierarchies(SnapshotHierarchy root, Invalidator invalidator, WatchMode watchMode, Stream<File> unprovenHierarchies) {
        // Remove hierarchies that did not respond to a watch probe
        return unprovenHierarchies
            .reduce(root, (currentRoot, unprovenHierarchy) -> {
                if (hierarchies.remove(unprovenHierarchy)) {
                    watchMode.loggerForWarnings(LOGGER).warn(INVALIDATING_HIERARCHY_MESSAGE + " {}", unprovenHierarchy);
                    return invalidator.invalidate(unprovenHierarchy.getAbsolutePath(), currentRoot);
                } else {
                    return currentRoot;
                }
            }, nonCombining());
    }

    @CheckReturnValue
    private SnapshotHierarchy updateUnwatchableFilesOnBuildStart(SnapshotHierarchy root, Invalidator invalidator, List<File> unsupportedFileSystems) {
        SnapshotHierarchy newRoot = root;
        FileHierarchySet oldUnwatchableFiles = unwatchableFiles;
        unwatchableFiles = unsupportedFileSystems.stream()
            .reduce(FileHierarchySet.empty(), FileHierarchySet::plus, nonCombining());
        if (!oldUnwatchableFiles.equals(unwatchableFiles)) {
            // Remove previously unwatchable files, since they may become watchable.
            // If we register a watchable hierarchy, then there mustn't be anything in the VFS at that location.
            newRoot = invalidateUnwatchableHierarchies(newRoot, invalidator, oldUnwatchableFiles);
            // Remove current unwatchable files, since they still may be watched.
            newRoot = invalidateUnwatchableHierarchies(newRoot, invalidator, unwatchableFiles);

            hierarchies.removeIf(unwatchableFiles::contains);
            watchableFiles = buildWatchableFilesFromHierarchies(hierarchies);

            // Replay the watchable hierarchies since the end of last build, since they have become watchable.
            for (File watchableHierarchy : watchableHierarchiesSinceLastBuildFinish) {
                if (!unwatchableFiles.contains(watchableHierarchy)) {
                    doRegisterWatchableHierarchy(watchableHierarchy, newRoot);
                }
            }
        }
        return newRoot;
    }

    @CheckReturnValue
    private static SnapshotHierarchy invalidateUnwatchableHierarchies(SnapshotHierarchy root, Invalidator invalidator, FileHierarchySet unwatchableFiles) {
        InvalidatingRootVisitor invalidatingRootVisitor = new InvalidatingRootVisitor(root, invalidator);
        unwatchableFiles.visitRoots(invalidatingRootVisitor);
        return invalidatingRootVisitor.getNewRoot();
    }

    /**
     * Hierarchies in usage order, most recent first.
     */
    public Stream<File> stream() {
        return hierarchies.stream();
    }

    private void checkThatNothingExistsInNewWatchableHierarchy(String watchableHierarchy, SnapshotHierarchy vfsRoot) {
        vfsRoot.rootSnapshotsUnder(watchableHierarchy)
            .filter(snapshotRoot -> !isInWatchableHierarchy(snapshotRoot.getAbsolutePath()) && !ignoredForWatching(snapshotRoot))
            .findAny()
            .ifPresent(snapshotRoot -> {
                throw new IllegalStateException(String.format(
                    "Found existing snapshot at '%s' for unwatched hierarchy '%s'",
                    snapshotRoot.getAbsolutePath(),
                    watchableHierarchy
                ));
            });
    }

    public boolean ignoredForWatching(FileSystemLocationSnapshot snapshot) {
        return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK || immutableLocationsFilter.test(snapshot.getAbsolutePath());
    }

    public boolean isInWatchableHierarchy(String path) {
        return watchableFiles.contains(path);
    }

    public boolean shouldWatch(FileSystemLocationSnapshot snapshot) {
        return !ignoredForWatching(snapshot) && isInWatchableHierarchy(snapshot.getAbsolutePath());
    }

    private static class InvalidatingRootVisitor implements FileHierarchySet.RootVisitor {
        private SnapshotHierarchy newRoot;
        private final Invalidator invalidator;

        public InvalidatingRootVisitor(SnapshotHierarchy newRoot, Invalidator invalidator) {
            this.newRoot = newRoot;
            this.invalidator = invalidator;
        }

        @Override
        public void visitRoot(String absolutePath) {
            newRoot = invalidator.invalidate(absolutePath, newRoot);
        }

        public SnapshotHierarchy getNewRoot() {
            return newRoot;
        }
    }

    private static SnapshotHierarchy retainOnlyMatchingSnapshots(SnapshotHierarchy root, Invalidator invalidator, Predicate<FileSystemLocationSnapshot> shouldKeep) {
        InvalidatorVisitor visitor = new InvalidatorVisitor(root, invalidator, shouldKeep);
        root.rootSnapshots()
            .forEach(snapshotRoot -> snapshotRoot.accept(visitor));
        return visitor.root;
    }

    private static class InvalidatorVisitor implements FileSystemSnapshotHierarchyVisitor {
        private final Invalidator invalidator;
        private final Predicate<FileSystemLocationSnapshot> shouldKeep;

        private SnapshotHierarchy root;

        public InvalidatorVisitor(SnapshotHierarchy root, Invalidator invalidator, Predicate<FileSystemLocationSnapshot> shouldKeep) {
            this.root = root;
            this.invalidator = invalidator;
            this.shouldKeep = shouldKeep;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
            if (shouldKeep.test(snapshot)) {
                return SnapshotVisitResult.CONTINUE;
            } else {
                root = invalidator.invalidate(snapshot.getAbsolutePath(), root);
                return SnapshotVisitResult.SKIP_SUBTREE;
            }
        }
    }

    public interface Invalidator {
        SnapshotHierarchy invalidate(String absolutePath, SnapshotHierarchy currentRoot);
    }

}
