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
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.vfs.WatchMode;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.util.Collection;
import java.util.stream.Stream;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    protected final FileWatcherProbeRegistry probeRegistry;
    protected final WatchableHierarchies watchableHierarchies;
    protected FileHierarchySet watchedFiles = FileHierarchySet.empty();
    private ImmutableSet<File> probedHierarchies = ImmutableSet.of();

    public AbstractFileWatcherUpdater(
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies
    ) {
        this.probeRegistry = probeRegistry;
        this.watchableHierarchies = watchableHierarchies;
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        probeRegistry.registerProbe(watchableHierarchy);
        update(root);
    }

    @Override
    public final SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode) {
        watchableHierarchies.updateUnsupportedFileSystems(watchMode);
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildStart(root, createInvalidator(), watchMode);
        newRoot = doUpdateVfsOnBuildStarted(newRoot);
        if (root != newRoot) {
            update(newRoot);
        }
        return newRoot;
    }

    @CheckReturnValue
    protected abstract SnapshotHierarchy doUpdateVfsOnBuildStarted(SnapshotHierarchy root);

    @Override
    public void virtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        boolean contentsChanged = handleVirtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, root);
        if (contentsChanged) {
            update(root);
        }
    }

    protected abstract boolean handleVirtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root);

    @Override
    public SnapshotHierarchy updateVfsOnBuildFinished(SnapshotHierarchy root, WatchMode watchMode, int maximumNumberOfWatchedHierarchies) {
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildFinished(
            root,
            watchMode,
            watchedFiles::contains,
            maximumNumberOfWatchedHierarchies,
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
    public void triggerWatchProbe(String path) {
        probeRegistry.triggerWatchProbe(path);
    }

    protected abstract WatchableHierarchies.Invalidator createInvalidator();

    private void update(SnapshotHierarchy root) {
        watchedFiles = resolveWatchedFiles(watchableHierarchies, root);
        updateWatchesOnChangedWatchedFiles(watchedFiles);

        // Probe every hierarchy that is watched, even ones nested inside others
        ImmutableSet<File> oldProbedHierarchies = probedHierarchies;
        probedHierarchies = watchableHierarchies.stream()
            .filter(watchedFiles::contains)
            .collect(ImmutableSet.toImmutableSet());
        if (oldProbedHierarchies.equals(probedHierarchies)) {
            return;
        }

        oldProbedHierarchies.stream()
            .filter(oldProbedHierarchy -> !probedHierarchies.contains(oldProbedHierarchy))
            .forEach(probedHierarchy -> {
                File probeDirectory = probeRegistry.getProbeDirectory(probedHierarchy);
                probeRegistry.disarmWatchProbe(probedHierarchy);
                stopWatchingProbeDirectory(probeDirectory);
            });

        probedHierarchies.stream()
            .filter(newProbedHierarchy -> !oldProbedHierarchies.contains(newProbedHierarchy))
            .forEach(probedHierarchy -> {
                File probeDirectory = probeRegistry.getProbeDirectory(probedHierarchy);
                // Make sure the directory exists, this can be necessary when
                // included builds are evaluated with configuration cache
                //noinspection ResultOfMethodCallIgnored
                probeDirectory.mkdirs();
                startWatchingProbeDirectory(probeDirectory);
                probeRegistry.armWatchProbe(probedHierarchy);
            });
    }

    protected abstract void updateWatchesOnChangedWatchedFiles(FileHierarchySet watchedFiles);

    protected abstract void startWatchingProbeDirectory(File probeDirectory);

    protected abstract void stopWatchingProbeDirectory(File probeDirectory);

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
}
