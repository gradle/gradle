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
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static org.gradle.internal.watch.registry.impl.Combiners.nonCombining;

public class WatchableHierarchies {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchableHierarchies.class);

    private final WatchableFileSystemDetector watchableFileSystemDetector;
    private final Predicate<String> watchFilter;

    /**
     * Hierarchies which can be watched.
     *
     * Those are normally project root directories from the current builds and from previous builds.
     */
    private FileHierarchySet watchableHierarchies = DefaultFileHierarchySet.of();

    /**
     * Hierarchies which do not support watching.
     *
     * Those are the mount points of file systems which do not support watching.
     */
    private FileHierarchySet unsupportedHierarchies = DefaultFileHierarchySet.of();
    private final Deque<File> recentlyUsedHierarchies = new ArrayDeque<>();
    private final Map<File, WatchProbe> watchProbesByHierarchy = new ConcurrentHashMap<>();
    private final Map<String, WatchProbe> watchProbesByPath = new ConcurrentHashMap<>();

    public WatchableHierarchies(WatchableFileSystemDetector watchableFileSystemDetector, Predicate<String> watchFilter) {
        this.watchableFileSystemDetector = watchableFileSystemDetector;
        this.watchFilter = watchFilter;
    }

    public void registerWatchableHierarchy(File watchableHierarchy, File probeFile, SnapshotHierarchy root) {
        String watchableHierarchyPath = watchableHierarchy.getAbsolutePath();
        if (!watchFilter.test(watchableHierarchyPath)) {
            throw new IllegalStateException(String.format(
                "Unable to watch directory '%s' since it is within Gradle's caches",
                watchableHierarchyPath
            ));
        }
        if (unsupportedHierarchies.contains(watchableHierarchyPath)) {
            LOGGER.info("Not watching {} since the file system is not supported", watchableHierarchy);
            return;
        }
        if (!watchableHierarchies.contains(watchableHierarchyPath)) {
            checkThatNothingExistsInNewWatchableHierarchy(watchableHierarchyPath, root);
            recentlyUsedHierarchies.addFirst(watchableHierarchy);
            watchableHierarchies = watchableHierarchies.plus(watchableHierarchy);
        } else {
            recentlyUsedHierarchies.remove(watchableHierarchy);
            recentlyUsedHierarchies.addFirst(watchableHierarchy);
        }
        WatchProbe watchProbe = new WatchProbe(watchableHierarchy, probeFile);
        watchProbesByHierarchy.put(watchableHierarchy, watchProbe);
        watchProbesByPath.put(probeFile.getAbsolutePath(), watchProbe);
        LOGGER.info("Now considering {} as hierarchies to watch", recentlyUsedHierarchies);
    }

    public void armWatchProbe(File watchableHierarchy) {
        WatchProbe probe = watchProbesByHierarchy.get(watchableHierarchy);
        if (probe != null) {
            try {
                probe.arm();
            } catch (IOException e) {
                // TODO Should this be debug instead?
                LOGGER.warn("Could not arm watch probe for hierarchy {}", watchableHierarchy, e);
            }
        } else {
            // TODO Should this be debug instead?
            LOGGER.warn("Did not find watchable hierarchy to probe: {}", watchableHierarchy);
        }
    }

    /**
     * Triggers a watch probe at the given location if one exists.
     */
    public void triggerWatchProbes(String path) {
        WatchProbe probe = watchProbesByPath.get(path);
        if (probe != null) {
            probe.trigger();
        }
    }

    @CheckReturnValue
    public SnapshotHierarchy removeUnwatchableContent(SnapshotHierarchy root, WatchMode watchMode, Predicate<File> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
        SnapshotHierarchy newRoot;
        newRoot = removeWatchedHierarchiesOverLimit(root, isWatchedHierarchy, maximumNumberOfWatchedHierarchies, invalidator);
        newRoot = removeUnwatchedSnapshots(newRoot, invalidator);
        // When FSW is enabled by default, we discard any non-watchable file systems, but not if it's enabled explicitly
        if (!shouldWatchUnsupportedFileSystems(watchMode)) {
            newRoot = removeUnwatchableFileSystems(newRoot, invalidator);
        }
        return newRoot;
    }

    private SnapshotHierarchy removeUnwatchedSnapshots(SnapshotHierarchy root, Invalidator invalidator) {
        RemoveUnwatchedFiles removeUnwatchedFilesVisitor = new RemoveUnwatchedFiles(root, invalidator);
        root.snapshotRoots()
            .forEach(snapshotRoot -> snapshotRoot.accept(removeUnwatchedFilesVisitor));
        return removeUnwatchedFilesVisitor.getRootWithUnwatchedFilesRemoved();
    }

    private SnapshotHierarchy removeWatchedHierarchiesOverLimit(SnapshotHierarchy root, Predicate<File> isWatchedHierarchy, int maximumNumberOfWatchedHierarchies, Invalidator invalidator) {
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
                File locationToRemove = recentlyUsedHierarchies.removeLast();
                result = invalidator.invalidate(locationToRemove.toString(), result);
            }
        }
        this.watchableHierarchies = DefaultFileHierarchySet.of(recentlyUsedHierarchies);
        return result;
    }

    private SnapshotHierarchy removeUnwatchableFileSystems(SnapshotHierarchy root, Invalidator invalidator) {
        SnapshotHierarchy invalidatedRoot = invalidateUnsupportedFileSystems(root, invalidator);
        if (invalidatedRoot != root) {
            LOGGER.info("Some of the file system contents retained in the virtual file system are on file systems that Gradle doesn't support watching. " +
                "The relevant state was discarded to ensure changes to these locations are properly detected. " +
                "You can override this by explicitly enabling file system watching.");
        }
        return invalidatedRoot;
    }

    public SnapshotHierarchy removeUnprovenHierarchies(SnapshotHierarchy root, Invalidator invalidator) {
        SnapshotHierarchy newRoot = root;
        for (WatchProbe watchProbe : watchProbesByHierarchy.values()) {
            if (watchProbe.leftArmed()) {
                if (recentlyUsedHierarchies.remove(watchProbe.watchableHierarchy)) {
                    LOGGER.warn("Invalidating hierarchy because watch probe hasn't been triggered {}", watchProbe.watchableHierarchy);
                    newRoot = invalidator.invalidate(watchProbe.watchableHierarchy.getAbsolutePath(), newRoot);
                }
            }
        }
        watchProbesByHierarchy.clear();
        watchProbesByPath.clear();
        return newRoot;
    }

    private SnapshotHierarchy invalidateUnsupportedFileSystems(SnapshotHierarchy root, Invalidator invalidator) {
        try {
            return watchableFileSystemDetector.detectUnsupportedFileSystems()
                .reduce(
                    root,
                    (updatedRoot, fileSystem) -> invalidator.invalidate(fileSystem.getMountPoint().getAbsolutePath(), updatedRoot),
                    nonCombining()
                );
        } catch (NativeException e) {
            LOGGER.warn("Unable to list file systems to check whether they can be watched. The whole state of the virtual file system has been discarded. Reason: {}", e.getMessage());
            return root.empty();
        }
    }

    public Collection<File> getRecentlyUsedHierarchies() {
        return recentlyUsedHierarchies;
    }

    private void checkThatNothingExistsInNewWatchableHierarchy(String watchableHierarchy, SnapshotHierarchy vfsRoot) {
        vfsRoot.snapshotRootsUnder(watchableHierarchy)
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
        return watchableHierarchies.contains(path);
    }

    public boolean shouldWatch(FileSystemLocationSnapshot snapshot) {
        return !ignoredForWatching(snapshot) && isInWatchableHierarchy(snapshot.getAbsolutePath());
    }

    /**
     * Detects and updates the unsupported file systems.
     *
     * Depending on the watch mode, actually detecting the unsupported file systems may not be necessary.
     */
    public void updateUnsupportedFileSystems(WatchMode watchMode) {
        unsupportedHierarchies = shouldWatchUnsupportedFileSystems(watchMode)
            ? DefaultFileHierarchySet.of()
            : detectUnsupportedHierarchies();
    }

    private boolean shouldWatchUnsupportedFileSystems(WatchMode watchMode) {
        return watchMode != WatchMode.DEFAULT;
    }

    private FileHierarchySet detectUnsupportedHierarchies() {
        try {
            return watchableFileSystemDetector.detectUnsupportedFileSystems()
                .reduce(
                    DefaultFileHierarchySet.of(),
                    (fileHierarchySet, fileSystemInfo) -> fileHierarchySet.plus(fileSystemInfo.getMountPoint()),
                    nonCombining()
                );
        } catch (NativeException e) {
            LOGGER.warn("Unable to list file systems to check whether they can be watched. Assuming all file systems can be watched. Reason: {}", e.getMessage());
            return DefaultFileHierarchySet.of();
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

    private static class WatchProbe {
        public enum State {
            /**
             * Probe hasn't been armed yet.
             */
            UNARMED,

            /**
             * Probe file exists, waiting for event to arrive.
             */
            ARMED,

            /**
             * The expected event has arrived.
             */
            TRIGGERED
        }

        private final File watchableHierarchy;
        private final File probeFile;
        private State state = State.UNARMED;

        public WatchProbe(File watchableHierarchy, File probeFile) {
            this.watchableHierarchy = watchableHierarchy;
            this.probeFile = probeFile;
        }

        public synchronized void arm() throws IOException {
            state = State.ARMED;
            //noinspection ResultOfMethodCallIgnored
            probeFile.getParentFile().mkdirs();
            // Creating a new file should be enough to trigger an event
            if (probeFile.createNewFile()) {
                return;
            }
            // If the file already existed, we touch it
            long lastModified;
            do {
                lastModified = System.currentTimeMillis();
            } while (lastModified == probeFile.lastModified());
            if (!probeFile.setLastModified(lastModified)) {
                throw new IOException("Couldn't arm watch probe: " + probeFile);
            }
        }

        public synchronized void trigger() {
            LOGGER.debug("Watch probe has been triggered for hierarchyt: {}", watchableHierarchy);
            state = State.TRIGGERED;
        }

        public synchronized boolean leftArmed() {
            return state == State.ARMED;
        }
    }
}
