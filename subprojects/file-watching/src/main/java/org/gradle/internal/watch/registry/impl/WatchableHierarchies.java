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

import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.watch.vfs.WatchMode;
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Predicate;

import static org.gradle.internal.watch.registry.impl.Combiners.nonCombining;

public class WatchableHierarchies {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchableHierarchies.class);

    private final WatchableFileSystemDetector watchableFileSystemDetector;
    private final Predicate<String> watchFilter;

    private FileHierarchySet watchableHierarchies = DefaultFileHierarchySet.of();
    private final Deque<Path> recentlyUsedHierarchies = new ArrayDeque<>();

    public WatchableHierarchies(WatchableFileSystemDetector watchableFileSystemDetector, Predicate<String> watchFilter) {
        this.watchableFileSystemDetector = watchableFileSystemDetector;
        this.watchFilter = watchFilter;
    }

    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        Path watchableHierarchyPath = watchableHierarchy.toPath().toAbsolutePath();
        String watchableHierarchyPathString = watchableHierarchyPath.toString();
        if (!watchFilter.test(watchableHierarchyPathString)) {
            throw new IllegalStateException(String.format(
                "Unable to watch directory '%s' since it is within Gradle's caches",
                watchableHierarchyPathString
            ));
        }
        if (!watchableHierarchies.contains(watchableHierarchyPathString)) {
            checkThatNothingExistsInNewWatchableHierarchy(watchableHierarchyPathString, root);
            recentlyUsedHierarchies.addFirst(watchableHierarchyPath);
            watchableHierarchies = watchableHierarchies.plus(watchableHierarchy);
        } else {
            recentlyUsedHierarchies.remove(watchableHierarchyPath);
            recentlyUsedHierarchies.addFirst(watchableHierarchyPath);
        }
        LOGGER.info("Now considering {} as hierarchies to watch", recentlyUsedHierarchies);
    }

    @CheckReturnValue
    public SnapshotHierarchy removeUnwatchableContent(SnapshotHierarchy root, WatchMode watchMode, Predicate<Path> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
        SnapshotHierarchy newRoot;
        newRoot = removeWatchedHierarchiesOverLimit(root, isWatchedHierarchy, maximumNumberOfWatchedHierarchies, invalidator);
        newRoot = removeUnwatchedSnapshots(newRoot, invalidator);
        // When FSW is enabled by default, we discard any non-watchable file systems, but not if it's enabled explicitly
        if (watchMode == WatchMode.DEFAULT) {
            newRoot = removeUnwatchableFileSystems(newRoot, invalidator);
        }
        return newRoot;
    }

    private SnapshotHierarchy removeUnwatchedSnapshots(SnapshotHierarchy root, Invalidator invalidator) {
        RemoveUnwatchedFiles removeUnwatchedFilesVisitor = new RemoveUnwatchedFiles(root, invalidator);
        root.visitSnapshotRoots(snapshotRoot -> snapshotRoot.accept(removeUnwatchedFilesVisitor));
        return removeUnwatchedFilesVisitor.getRootWithUnwatchedFilesRemoved();
    }

    private SnapshotHierarchy removeWatchedHierarchiesOverLimit(SnapshotHierarchy root, Predicate<Path> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
        recentlyUsedHierarchies.removeIf(hierarchy -> !isWatchedHierarchy.test(hierarchy));
        SnapshotHierarchy result = root;
        int toRemove = recentlyUsedHierarchies.size() - maximumNumberOfWatchedHierarchies;
        if (toRemove > 0) {
            LOGGER.info(
                "Watching too many directories in the file system (watching {}, limit {}), dropping some state from the virtual file system",
                recentlyUsedHierarchies.size(),
                maximumNumberOfWatchedHierarchies
            );
            for (int i = 0; i < toRemove; i++) {
                Path locationToRemove = recentlyUsedHierarchies.removeLast();
                result = invalidator.invalidate(locationToRemove.toString(), result);
            }
        }
        this.watchableHierarchies = DefaultFileHierarchySet.of(recentlyUsedHierarchies.stream().map(Path::toFile)::iterator);
        return result;
    }

    private SnapshotHierarchy removeUnwatchableFileSystems(SnapshotHierarchy root, Invalidator invalidator) {
        SnapshotHierarchy invalidatedRoot = watchableFileSystemDetector.detectUnsupportedFileSystems()
            .reduce(
                root,
                (updatedRoot, fileSystem) -> invalidator.invalidate(fileSystem.getMountPoint().getAbsolutePath(), updatedRoot),
                nonCombining()
            );
        if (invalidatedRoot != root) {
            LOGGER.info("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching. " +
                "The relevant state was discarded to ensure changes to these locations are properly detected. " +
                "You can override this by explicitly enabling file system watching.");
        }
        return invalidatedRoot;
    }

    public Collection<Path> getWatchableHierarchies() {
        return recentlyUsedHierarchies;
    }

    private void checkThatNothingExistsInNewWatchableHierarchy(String watchableHierarchy, SnapshotHierarchy vfsRoot) {
        vfsRoot.visitSnapshotRoots(watchableHierarchy, snapshotRoot -> {
            if (!isInWatchableHierarchy(snapshotRoot.getAbsolutePath()) && !ignoredForWatching(snapshotRoot)) {
                throw new IllegalStateException(String.format(
                    "Found existing snapshot at '%s' for unwatched hierarchy '%s'",
                    snapshotRoot.getAbsolutePath(),
                    watchableHierarchy
                ));
            }
        });
    }

    public boolean ignoredForWatching(FileSystemLocationSnapshot snapshot) {
        return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK || !watchFilter.test(snapshot.getAbsolutePath());
    }

    public boolean isInWatchableHierarchy(String path) {
        return watchableHierarchies.contains(path);
    }

    public boolean shouldWatch(FileSystemLocationSnapshot snapshot) {
        return !ignoredForWatching(snapshot) && isInWatchableHierarchy(snapshot.getAbsolutePath());
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
