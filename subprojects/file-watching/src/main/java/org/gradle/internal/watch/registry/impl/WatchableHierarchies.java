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

import net.rubygrapefruit.platform.NativeException;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.vfs.WatchMode;
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.gradle.internal.watch.registry.impl.Combiners.nonCombining;

public class WatchableHierarchies {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchableHierarchies.class);

    public static final String INVALIDATING_HIERARCHY_MESSAGE = "Invalidating hierarchy because watch probe hasn't been triggered";

    private final WatchableFileSystemDetector watchableFileSystemDetector;
    private final FileWatcherProbeRegistry probeRegistry;
    private final Predicate<String> watchFilter;

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
        FileWatcherProbeRegistry probeRegistry,
        WatchableFileSystemDetector watchableFileSystemDetector,
        Predicate<String> watchFilter
    ) {
        this.probeRegistry = probeRegistry;
        this.watchableFileSystemDetector = watchableFileSystemDetector;
        this.watchFilter = watchFilter;
    }

    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        String watchableHierarchyPath = watchableHierarchy.getAbsolutePath();
        if (!watchFilter.test(watchableHierarchyPath)) {
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
    public SnapshotHierarchy removeUnwatchableContentOnBuildFinished(SnapshotHierarchy root, WatchMode watchMode, Predicate<File> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
        SnapshotHierarchy newRoot;
        newRoot = removeWatchedHierarchiesOverLimit(root, isWatchedHierarchy, maximumNumberOfWatchedHierarchies, invalidator);
        newRoot = removeUnwatchedSnapshots(newRoot, invalidator);
        // When FSW is enabled by default, we discard any non-watchable file systems, but not if it's enabled explicitly
        if (!shouldWatchUnsupportedFileSystems(watchMode)) {
            newRoot = removeUnwatchableFileSystems(newRoot, invalidator);
        }
        watchableHierarchiesSinceLastBuildFinish.clear();
        return newRoot;
    }

    @CheckReturnValue
    private SnapshotHierarchy removeUnwatchedSnapshots(SnapshotHierarchy root, Invalidator invalidator) {
        RemoveUnwatchedFiles removeUnwatchedFilesVisitor = new RemoveUnwatchedFiles(root, invalidator);
        root.rootSnapshots()
            .forEach(snapshotRoot -> snapshotRoot.accept(removeUnwatchedFilesVisitor));
        return removeUnwatchedFilesVisitor.getRootWithUnwatchedFilesRemoved();
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
            .reduce(FileHierarchySet.empty(), FileHierarchySet::plus, Combiners.nonCombining());
    }

    @CheckReturnValue
    private SnapshotHierarchy removeUnwatchableFileSystems(SnapshotHierarchy root, Invalidator invalidator) {
        SnapshotHierarchy invalidatedRoot = invalidateUnsupportedFileSystems(root, invalidator);
        if (invalidatedRoot != root) {
            LOGGER.info("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching. " +
                "The relevant state was discarded to ensure changes to these locations are properly detected. " +
                "You can override this by explicitly enabling file system watching.");
        }
        return invalidatedRoot;
    }

    @CheckReturnValue
    public SnapshotHierarchy removeUnwatchableContentOnBuildStart(SnapshotHierarchy root, Invalidator invalidator, WatchMode watchMode) {
        SnapshotHierarchy newRoot = root;
        newRoot = removeUnprovenHierarchies(newRoot, invalidator, watchMode);
        newRoot = updateUnwatchableFilesOnBuildStart(newRoot, invalidator, watchMode);
        return newRoot;
    }

    @CheckReturnValue
    private SnapshotHierarchy removeUnprovenHierarchies(SnapshotHierarchy root, Invalidator invalidator, WatchMode watchMode) {
        return probeRegistry.unprovenHierarchies()
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
    private SnapshotHierarchy invalidateUnsupportedFileSystems(SnapshotHierarchy root, Invalidator invalidator) {
        try {
            return watchableFileSystemDetector.detectUnsupportedFileSystems()
                .reduce(
                    root,
                    (updatedRoot, fileSystem) -> invalidator.invalidate(fileSystem.getAbsolutePath(), updatedRoot),
                    nonCombining()
                );
        } catch (NativeException e) {
            LOGGER.warn("Unable to list file systems to check whether they can be watched. The whole state of the virtual file system has been discarded. Reason: {}", e.getMessage());
            return root.empty();
        }
    }

    @CheckReturnValue
    private SnapshotHierarchy updateUnwatchableFilesOnBuildStart(SnapshotHierarchy root, Invalidator invalidator, WatchMode watchMode) {
        SnapshotHierarchy newRoot = root;
        FileHierarchySet oldUnwatchableFiles = unwatchableFiles;
        unwatchableFiles = shouldWatchUnsupportedFileSystems(watchMode)
            ? FileHierarchySet.empty()
            : detectUnsupportedHierarchies();
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
    private SnapshotHierarchy invalidateUnwatchableHierarchies(SnapshotHierarchy root, Invalidator invalidator, FileHierarchySet unwatchableFiles) {
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
        return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK || !watchFilter.test(snapshot.getAbsolutePath());
    }

    public boolean isInWatchableHierarchy(String path) {
        return watchableFiles.contains(path);
    }

    public boolean shouldWatch(FileSystemLocationSnapshot snapshot) {
        return !ignoredForWatching(snapshot) && isInWatchableHierarchy(snapshot.getAbsolutePath());
    }

    private boolean shouldWatchUnsupportedFileSystems(WatchMode watchMode) {
        return watchMode != WatchMode.DEFAULT;
    }

    private FileHierarchySet detectUnsupportedHierarchies() {
        try {
            return watchableFileSystemDetector.detectUnsupportedFileSystems()
                .reduce(FileHierarchySet.empty(), FileHierarchySet::plus, nonCombining());
        } catch (NativeException e) {
            LOGGER.warn("Unable to list file systems to check whether they can be watched. Assuming all file systems can be watched. Reason: {}", e.getMessage());
            return FileHierarchySet.empty();
        }
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

    private class RemoveUnwatchedFiles implements FileSystemSnapshotHierarchyVisitor {
        private SnapshotHierarchy root;
        private final Invalidator invalidator;

        public RemoveUnwatchedFiles(SnapshotHierarchy root, Invalidator invalidator) {
            this.root = root;
            this.invalidator = invalidator;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
            if (shouldBeRemoved(snapshot)) {
                invalidateUnwatchedFile(snapshot);
                return SnapshotVisitResult.SKIP_SUBTREE;
            } else {
                return SnapshotVisitResult.CONTINUE;
            }
        }

        private boolean shouldBeRemoved(FileSystemLocationSnapshot snapshot) {
            //noinspection UnnecessaryParentheses
            return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK ||
                (!isInWatchableHierarchy(snapshot.getAbsolutePath()) && watchFilter.test(snapshot.getAbsolutePath()));
        }

        private void invalidateUnwatchedFile(FileSystemLocationSnapshot snapshot) {
            root = invalidator.invalidate(snapshot.getAbsolutePath(), root);
        }

        public SnapshotHierarchy getRootWithUnwatchedFilesRemoved() {
            return root;
        }
    }

    public interface Invalidator {
        SnapshotHierarchy invalidate(String absolutePath, SnapshotHierarchy currentRoot);
    }

}
