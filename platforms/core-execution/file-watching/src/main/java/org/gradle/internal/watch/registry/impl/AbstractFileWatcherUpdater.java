/*
 * Copyright 2021 the original author or authors.
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
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.Combiners;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.registry.WatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileWatcherUpdater.class);

    protected final FileWatcherProbeRegistry probeRegistry;
    protected final WatchableHierarchies watchableHierarchies;
    private final MovedDirectoryHandler movedDirectoryHandler;
    protected FileHierarchySet watchedFiles = FileHierarchySet.empty();

    public AbstractFileWatcherUpdater(
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies,
        MovedDirectoryHandler movedDirectoryHandler
    ) {
        this.probeRegistry = probeRegistry;
        this.watchableHierarchies = watchableHierarchies;
        this.movedDirectoryHandler = movedDirectoryHandler;
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root, File probeLocation) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        probeRegistry.registerProbe(watchableHierarchy, probeLocation);
        update(root);
    }

    @Override
    public final SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode, List<File> unsupportedFileSystems) {
        Stream<File> unprovenHierarchies = probeRegistry.unprovenHierarchies();
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildStart(root, createInvalidator(), watchMode, unsupportedFileSystems, unprovenHierarchies);
        newRoot = invalidateMovedDirectoriesOnBuildStarted(newRoot);
        if (root != newRoot) {
            update(newRoot);
        }
        return newRoot;
    }

    @CheckReturnValue
    private SnapshotHierarchy invalidateMovedDirectoriesOnBuildStarted(SnapshotHierarchy root) {
        SnapshotHierarchy newRoot = root;
        WatchableHierarchies.Invalidator invalidator = createInvalidator();
        for (File movedDirectory : movedDirectoryHandler.stopWatchingMovedDirectories(root)) {
            LOGGER.info("Dropping VFS state for moved directory {}", movedDirectory.getAbsolutePath());
            newRoot = invalidator.invalidate(movedDirectory.getAbsolutePath(), newRoot);
        }
        return newRoot;
    }

    @Override
    public void virtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        boolean contentsChanged = handleVirtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, root);
        if (contentsChanged) {
            update(root);
        }
    }

    protected abstract boolean handleVirtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root);

    @Override
    public SnapshotHierarchy updateVfsBeforeBuildFinished(SnapshotHierarchy root, int maximumNumberOfWatchedHierarchies, List<File> unsupportedFileSystems) {
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentBeforeBuildFinished(
            root,
            watchedFiles::contains,
            maximumNumberOfWatchedHierarchies,
            unsupportedFileSystems,
            createInvalidator()
        );

        if (root != newRoot) {
            update(newRoot);
        }
        return newRoot;
    }

    @Override
    public SnapshotHierarchy updateVfsBeforeBuildFinished(SnapshotHierarchy root) {
        SnapshotHierarchy newRoot = WatchableHierarchies.removeUnwatchableContentAfterBuildFinished(
            root,
            createInvalidator()
        );

        if (root != newRoot) {
            update(newRoot);
        }
        return newRoot;
    }

    @Override
    public FileHierarchySet getWatchedFiles() {
        return watchedFiles;
    }

    @Override
    public void triggerWatchProbe(Path path) {
        probeRegistry.triggerWatchProbe(path);
    }

    protected abstract WatchableHierarchies.Invalidator createInvalidator();

    private void update(SnapshotHierarchy root) {
        FileHierarchySet oldWatchedFiles = watchedFiles;
        watchedFiles = resolveWatchedFiles(watchableHierarchies, root);
        if (!watchedFiles.equals(oldWatchedFiles)) {
            updateWatchesOnChangedWatchedFiles(watchedFiles);
        }

        ImmutableSet<File> probedHierarchies = watchableHierarchies.stream()
            .filter(watchedFiles::contains)
            .collect(ImmutableSet.toImmutableSet());

        probeRegistry.updateProbedHierarchies(
            probedHierarchies,
            this::stopWatchingProbeDirectory,
            (probeLocation, isSubdirectoryOfWatchedHierarchy) -> {
            // Make sure the directory exists, this can be necessary when
            // included builds are evaluated with configuration cache
            //noinspection ResultOfMethodCallIgnored
                probeLocation.mkdirs();
                startWatchingProbeDirectory(probeLocation, isSubdirectoryOfWatchedHierarchy);
            }
        );
    }

    protected abstract void updateWatchesOnChangedWatchedFiles(FileHierarchySet newWatchedFiles);

    protected abstract void startWatchingProbeDirectory(File probeDirectory, boolean isSubdirectoryOfWatchedHierarchy);

    protected abstract void stopWatchingProbeDirectory(File probeDirectory, boolean isSubdirectoryOfWatchedHierarchy);

    @VisibleForTesting
    static FileHierarchySet resolveWatchedFiles(WatchableHierarchies watchableHierarchies, SnapshotHierarchy vfsRoot) {
        return watchableHierarchies.stream()
            .map(File::getPath)
            .filter(watchableHierarchy -> hasWatchableContent(vfsRoot.rootSnapshotsUnder(watchableHierarchy), watchableHierarchies))
            .reduce(FileHierarchySet.empty(), FileHierarchySet::plus, Combiners.nonCombining());
    }

    private static boolean hasWatchableContent(Stream<FileSystemLocationSnapshot> snapshots, WatchableHierarchies watchableHierarchies) {
        return snapshots
            .anyMatch(snapshot -> !isMissing(snapshot) && !watchableHierarchies.ignoredForWatching(snapshot));
    }

    private static boolean isMissing(FileSystemLocationSnapshot snapshot) {
        // Missing accessed indirectly means we have a dangling symlink in the directory, and that's content we cannot ignore
        return snapshot.getType() == FileType.Missing && snapshot.getAccessType() == FileMetadata.AccessType.DIRECT;
    }

    public interface MovedDirectoryHandler {
        /**
         * Stop watching the moved directories that have been moved without any notifications.
         *
         * When a directory is moved, then under some circumstances there won't be any notifications.
         *
         * On Windows when watched directories are moved, the OS does not send a notification,
         * even though the VFS should be updated.
         *
         * On Linux, when you move the parent directory of a watched directory, then there isn't a notification.
         *
         * Our best bet here is to cull any moved watched directories from the VFS at the start of every build.
         */
        Collection<File> stopWatchingMovedDirectories(SnapshotHierarchy vfsRoot);
    }
}
