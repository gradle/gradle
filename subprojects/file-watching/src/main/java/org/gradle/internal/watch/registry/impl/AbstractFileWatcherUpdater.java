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
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.io.File;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    private final Predicate<String> watchFilter;

    private FileHierarchySet allowedDirectoriesToWatch = DefaultFileHierarchySet.of();

    public AbstractFileWatcherUpdater(Predicate<String> watchFilter) {
        this.watchFilter = watchFilter;
    }

    protected void recordDiscoveredHierarchyToWatch(File discoveredHierarchy, SnapshotHierarchy root) {
        String discoveredHierarchyAbsolutePath = discoveredHierarchy.getAbsolutePath();
        if (!watchFilter.test(discoveredHierarchyAbsolutePath)) {
            throw new RuntimeException(String.format(
                "Unable to watch directory '%s' since it is within Gradle's caches",
                discoveredHierarchyAbsolutePath
            ));
        }
        if (!allowedDirectoriesToWatch.contains(discoveredHierarchyAbsolutePath)) {
            checkThatNothingExistsInNewHierarchyToWatch(discoveredHierarchyAbsolutePath, root);
        }
        allowedDirectoriesToWatch = allowedDirectoriesToWatch.plus(discoveredHierarchy);
    }

    protected SnapshotHierarchy recordAllowedHierarchiesToWatchAtEndOfBuild(Stream<File> allowedDirectoriesToWatch, SnapshotHierarchy root, Invalidator invalidator) {
        this.allowedDirectoriesToWatch = DefaultFileHierarchySet.of(allowedDirectoriesToWatch::iterator);
        RemoveUnwatchedFiles removeUnwatchedFilesVisitor = new RemoveUnwatchedFiles(root, invalidator);
        root.visitSnapshotRoots(snapshotRoot -> snapshotRoot.accept(removeUnwatchedFilesVisitor));
        return removeUnwatchedFilesVisitor.getRootWithUnwatchedFilesRemoved();
    }

    private void checkThatNothingExistsInNewHierarchyToWatch(String hierarchyToWatchPath, SnapshotHierarchy root) {
        root.visitSnapshotRoots(hierarchyToWatchPath, snapshotRoot -> {
            if (!isInAllowedHierarchyToWatch(snapshotRoot.getAbsolutePath()) && !ignoredForWatching(snapshotRoot)) {
                throw new RuntimeException(String.format(
                    "Found existing snapshot at '%s' for unwatched hierarchy '%s'",
                    snapshotRoot.getAbsolutePath(),
                    hierarchyToWatchPath));
            }
        });
    }

    protected boolean ignoredForWatching(CompleteFileSystemLocationSnapshot snapshot) {
        return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK || !watchFilter.test(snapshot.getAbsolutePath());
    }

    protected boolean isInAllowedHierarchyToWatch(String path) {
        return allowedDirectoriesToWatch.contains(path);
    }

    protected class CheckIfNonEmptySnapshotVisitor implements SnapshotHierarchy.SnapshotVisitor {
        private boolean empty = true;
        private boolean onlyMissing = true;

        @Override
        public void visitSnapshotRoot(CompleteFileSystemLocationSnapshot rootSnapshot) {
            if (!ignoredForWatching(rootSnapshot)) {
                empty = false;
                if (rootSnapshot.getType() != FileType.Missing) {
                    onlyMissing = false;
                }
            }
        }

        public boolean isEmpty() {
            return empty;
        }

        public boolean containsOnlyMissingFiles() {
            return onlyMissing;
        }
    }

    protected class RemoveUnwatchedFiles implements FileSystemSnapshotVisitor {
        private SnapshotHierarchy root;
        private final Invalidator invalidator;

        public RemoveUnwatchedFiles(SnapshotHierarchy root, Invalidator invalidator) {
            this.root = root;
            this.invalidator = invalidator;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            if (shouldBeRemoved(directorySnapshot)) {
                invalidateUnwatchedFile(directorySnapshot);
                return false;
            }
            return true;
        }

        private boolean shouldBeRemoved(CompleteFileSystemLocationSnapshot snapshot) {
            return snapshot.getAccessType() == FileMetadata.AccessType.VIA_SYMLINK ||
                (!isInWatchedDir(snapshot) && watchFilter.test(snapshot.getAbsolutePath()));
        }

        private boolean isInWatchedDir(CompleteFileSystemLocationSnapshot snapshot) {
            return allowedDirectoriesToWatch.contains(snapshot.getAbsolutePath());
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            if (shouldBeRemoved(fileSnapshot)) {
                invalidateUnwatchedFile(fileSnapshot);
            }
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }

        private void invalidateUnwatchedFile(CompleteFileSystemLocationSnapshot snapshot) {
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
