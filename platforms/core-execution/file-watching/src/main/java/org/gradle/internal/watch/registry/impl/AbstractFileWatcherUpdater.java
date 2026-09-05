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
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.internal.watch.registry.WatcherVerificationResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public abstract class AbstractFileWatcherUpdater implements FileWatcherUpdater {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractFileWatcherUpdater.class);

    /**
     * Bounds the whole verification step. Soft: a {@code stat} that blocks is not interrupted by it,
     * since the walk runs on the build thread.
     */
    private static final long VERIFICATION_DEADLINE_MILLIS = 2000;

    protected final FileWatcherProbeRegistry probeRegistry;
    protected final WatchableHierarchies watchableHierarchies;
    private final MovedDirectoryHandler movedDirectoryHandler;
    protected FileHierarchySet watchedFiles = FileHierarchySet.empty();
    /**
     * Hierarchies whose probes are armed. Replaced wholesale rather than mutated, and read by the build
     * thread outside the update lock, so the write has to publish the whole set.
     */
    private volatile ImmutableSet<File> probedHierarchies = ImmutableSet.of();

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
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        watchableHierarchies.registerWatchableHierarchy(watchableHierarchy, root);
        probeRegistry.registerProbe(watchableHierarchy);
        update(root);
    }

    @Override
    public final SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode, List<File> unsupportedFileSystems, WatcherVerificationResult verification) {
        SnapshotHierarchy invalidatedRoot = root;
        WatchableHierarchies.Invalidator invalidator = createInvalidator();
        for (String outdatedPath : verification.getOutdatedPaths()) {
            invalidatedRoot = invalidator.invalidate(outdatedPath, invalidatedRoot);
        }
        SnapshotHierarchy newRoot = watchableHierarchies.removeUnwatchableContentOnBuildStart(
            invalidatedRoot, createInvalidator(), watchMode, unsupportedFileSystems, verification.getVerifiedHierarchies());
        newRoot = invalidateMovedDirectoriesOnBuildStarted(newRoot);
        if (root != newRoot) {
            update(newRoot);
        }
        return newRoot;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The root is read outside the virtual file system's update lock and the result is applied to
     * whatever root the caller holds once it takes it. That is safe because of what can arrive in
     * between: the only paths that add a snapshot are {@code store} and {@code storeWithAction}, and
     * both store a location that was just read from disk. This scan exists to decide whether state
     * retained from an <em>earlier</em> build can still be trusted, and a snapshot taken after the scan
     * started is not such state. The watcher's own thread never adds — it only invalidates, or empties
     * the whole hierarchy after an error.</p>
     */
    @Override
    public WatcherVerificationResult verifyWatcherIsCurrent(SnapshotHierarchy root) {
        ImmutableSet<File> hierarchiesToProve = probedHierarchies;
        if (hierarchiesToProve.isEmpty()) {
            return WatcherVerificationResult.EMPTY;
        }
        // The deadline covers the preparation as well as the walk: arming sweeps a directory, creates
        // it and writes a file, per hierarchy, and on a stalled file system that is unbounded. It does
        // not govern the preparation: every hierarchy is re-armed regardless. Skipping one
        // would leave its probe TRIGGERED from the previous build, out of unprovenHierarchies(), and
        // its retained state kept on that stale evidence, which is the failure this step exists to
        // prevent. A slow arming instead eats the walk's share, so nothing is verified and everything
        // is dropped.
        // Elapsed time is measured as a difference rather than against a precomputed instant:
        // System.nanoTime() has an arbitrary origin and may overflow, so only the subtraction is
        // ordered correctly. Its own javadoc says as much.
        long startedAt = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(VERIFICATION_DEADLINE_MILLIS);
        hierarchiesToProve.forEach(probeRegistry::rearmWatchProbe);

        // Computed once per verification, after re-arming, because arming can create the directory.
        // The walk asks about Gradle's own artifacts once per snapshot child and once per on-disk
        // name, so both questions have to be set lookups rather than file system calls.
        ImmutableSet<File> probeDirectories = hierarchiesToProve.stream()
            .map(probeRegistry::getProbeDirectory)
            .collect(ImmutableSet.toImmutableSet());
        List<String> outdatedPaths = new ArrayList<>();
        ImmutableSet.Builder<File> verifiedHierarchies = ImmutableSet.builder();
        for (File hierarchy : hierarchiesToProve) {
            if (!probeRegistry.hasUnprovenHierarchies() || System.nanoTime() - startedAt > timeoutNanos) {
                // The probe answered, or the deadline passed, before this hierarchy was entered.
                break;
            }
            RetainedStateVerifier verifier = new RetainedStateVerifier(startedAt, timeoutNanos, probeDirectories);
            Iterator<FileSystemLocationSnapshot> rootSnapshots =
                root.rootSnapshotsUnder(hierarchy.getAbsolutePath()).iterator();
            while (rootSnapshots.hasNext() && !verifier.isAbandoned()) {
                rootSnapshots.next().accept(verifier);
            }
            outdatedPaths.addAll(verifier.getOutdatedPaths());
            if (verifier.isAbandoned()) {
                // The probe answered, or the deadline passed. Either way no later hierarchy is entered.
                break;
            }
            verifiedHierarchies.add(hierarchy);
        }
        return new WatcherVerificationResult(outdatedPaths, verifiedHierarchies.build());
    }

    /**
     * Compares each retained snapshot against the file system, collecting the locations that no longer
     * match.
     *
     * <p>Regular files are compared by size and modification time, directories by the names of their
     * children; no content is hashed, so the walk costs one {@code stat} per entry. The probe directory
     * is walked like any other. What is dropped from both sides of a listing comparison is the
     * artifacts Gradle writes for its own signalling, since a name Gradle just wrote is not evidence
     * about an external change.</p>
     */
    private class RetainedStateVerifier implements FileSystemSnapshotHierarchyVisitor {
        private static final int ENTRIES_BETWEEN_PROBE_CHECKS = 1024;

        private final long startedAt;
        private final long timeoutNanos;
        private final ImmutableSet<File> probeDirectories;
        private final List<String> outdatedPaths = new ArrayList<>();
        private int entriesVisited;
        private boolean abandoned;

        RetainedStateVerifier(long startedAt, long timeoutNanos, ImmutableSet<File> probeDirectories) {
            this.startedAt = startedAt;
            this.timeoutNanos = timeoutNanos;
            this.probeDirectories = probeDirectories;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
            if (abandoned) {
                return SnapshotVisitResult.TERMINATE;
            }
            if (++entriesVisited % ENTRIES_BETWEEN_PROBE_CHECKS == 0
                && (!probeRegistry.hasUnprovenHierarchies() || System.nanoTime() - startedAt > timeoutNanos)) {
                abandoned = true;
                return SnapshotVisitResult.TERMINATE;
            }
            String path = snapshot.getAbsolutePath();
            if (watchableHierarchies.ignoredForWatching(snapshot)) {
                return SnapshotVisitResult.SKIP_SUBTREE;
            }
            if (matchesFileSystem(snapshot)) {
                return SnapshotVisitResult.CONTINUE;
            }
            outdatedPaths.add(path);
            return SnapshotVisitResult.SKIP_SUBTREE;
        }

        /**
         * Returns whether the child is one Gradle writes for its own signalling: a probe file inside a
         * probe directory, or a probe directory in the listing of the directory that contains it.
         *
         * <p>Excluding the directory unconditionally accepts one stale claim: a parent snapshotted
         * before Gradle created the directory keeps a children set that no longer names it, and a
         * {@code DirectorySnapshot} asserts a <em>complete</em> listing, so the virtual file system
         * answers "missing" from memory for any path beneath it. The bound on that is a scope — paths
         * under one directory Gradle owns — rather than a time. It is taken because the alternative,
         * reporting the parent when the directory appears, invalidates the whole hierarchy's retained
         * state over a directory Gradle itself just made.</p>
         *
         * <p>It does not outlive a working watcher. The probe filter suppresses only the broadcast leg,
         * so {@code InvalidateVfsChangeHandler} still invalidates for a probe path, and
         * {@code DirectorySnapshot.invalidate} degrades the node to a partial one even when the path
         * matches no child — which is exactly this case. Gradle writes such an event itself every build
         * when it re-arms.</p>
         */
        private boolean isGradlesOwnChild(File directory, String name) {
            File child = new File(directory, name);
            if (probeDirectories.contains(directory)) {
                return isProbeFile(child.getAbsolutePath());
            }
            return probeDirectories.contains(child);
        }

        private boolean matchesFileSystem(FileSystemLocationSnapshot snapshot) {
            File file = new File(snapshot.getAbsolutePath());
            BasicFileAttributes attributes = readAttributes(file);
            if (attributes != null && attributes.isSymbolicLink()) {
                // The retained snapshot recorded direct access, so a link that has appeared since is a
                // change whatever it points at. Comparing through it would compare the target, and a
                // matching target would vouch for content reached by a route Gradle does not watch.
                return false;
            }
            switch (snapshot.getType()) {
                case RegularFile:
                    if (attributes == null || !attributes.isRegularFile()) {
                        return false;
                    }
                    FileMetadata metadata = ((RegularFileSnapshot) snapshot).getMetadata();
                    return attributes.size() == metadata.getLength()
                        && attributes.lastModifiedTime().toMillis() == metadata.getLastModified();
                case Directory:
                    if (attributes == null || !attributes.isDirectory()) {
                        return false;
                    }
                    String[] namesOnDisk = file.list();
                    if (namesOnDisk == null) {
                        return false;
                    }
                    Set<String> expected = new HashSet<>();
                    for (FileSystemLocationSnapshot child : ((DirectorySnapshot) snapshot).getChildren()) {
                        if (child.getType() != FileType.Missing && !isGradlesOwnChild(file, child.getName())) {
                            expected.add(child.getName());
                        }
                    }
                    Set<String> found = new HashSet<>();
                    for (String name : namesOnDisk) {
                        if (!isGradlesOwnChild(file, name)) {
                            found.add(name);
                        }
                    }
                    // Both directions: a name only on disk makes the retained listing stale just as a
                    // missing one does.
                    return expected.equals(found);
                case Missing:
                    return attributes == null;
                default:
                    return false;
            }
        }

        List<String> getOutdatedPaths() {
            return outdatedPaths;
        }

        boolean isAbandoned() {
            return abandoned;
        }
    }

    /**
     * Reads the attributes of the path itself rather than of whatever it may link to, so a path that
     * has become a symlink is visible as one.
     */
    @Nullable
    private static BasicFileAttributes readAttributes(File file) {
        try {
            return Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return null;
        }
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
    public SnapshotHierarchy updateVfsBeforeAfterFinished(SnapshotHierarchy root) {
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
    public void removeProbeFiles() {
        probeRegistry.removeProbeFiles();
    }

    @Override
    public boolean isProbeFile(String path) {
        return probeRegistry.isProbeFile(path);
    }

    @Override
    public boolean isProbeDirectory(String path) {
        return probeRegistry.isProbeDirectory(path);
    }

    @Override
    public void triggerWatchProbe(String path) {
        probeRegistry.triggerWatchProbe(path);
    }

    protected abstract WatchableHierarchies.Invalidator createInvalidator();

    private void update(SnapshotHierarchy root) {
        FileHierarchySet oldWatchedFiles = watchedFiles;
        watchedFiles = resolveWatchedFiles(watchableHierarchies, root);
        if (!watchedFiles.equals(oldWatchedFiles)) {
            updateWatchesOnChangedWatchedFiles(watchedFiles);
        }

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

    protected abstract void updateWatchesOnChangedWatchedFiles(FileHierarchySet newWatchedFiles);

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
