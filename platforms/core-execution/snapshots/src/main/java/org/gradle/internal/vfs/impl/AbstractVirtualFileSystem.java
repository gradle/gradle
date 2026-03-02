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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.VfsRelativePath;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVirtualFileSystem.class);

    /**
     * State for project files and other watched locations. Subject to file-event invalidations.
     * Guarded by watchedState's lock.
     */
    protected final VfsState watchedState;

    /**
     * State for global cache paths (Gradle user home caches). These are Gradle-managed and
     * never invalidated by file events, so they can be updated independently without
     * contending with watched-path state and invalidations.
     * Guarded by globalCacheState's lock.
     */
    private final VfsState globalCacheState;

    private final Predicate<String> globalCacheFilter;

    protected AbstractVirtualFileSystem(SnapshotHierarchy initialRoot, Predicate<String> globalCacheFilter) {
        VersionHierarchyRoot initialVersionRoot = VersionHierarchyRoot.empty(0, initialRoot.getCaseSensitivity());
        this.watchedState = new VfsState(initialRoot, initialVersionRoot);
        this.globalCacheState = new VfsState(initialRoot.empty(), initialVersionRoot);
        this.globalCacheFilter = globalCacheFilter;
    }

    public SnapshotHierarchy getWatchedRoot() {
        return watchedState.root;
    }

    protected void updateWatchedRootUnderLock(UnaryOperator<SnapshotHierarchy> updateFunction) {
        watchedState.underLock(() -> {
            SnapshotHierarchy currentRoot = watchedState.root;
            watchedState.root = updateFunction.apply(currentRoot);
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath) {
        return rootFor(absolutePath).findSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> findMetadata(String absolutePath) {
        return rootFor(absolutePath).findMetadata(absolutePath);
    }

    @Override
    public Stream<FileSystemLocationSnapshot> findRootSnapshotsUnder(String absolutePath) {
        return rootFor(absolutePath).rootSnapshotsUnder(absolutePath);
    }

    private SnapshotHierarchy rootFor(String absolutePath) {
        return globalCacheFilter.test(absolutePath) ? globalCacheState.root : watchedState.root;
    }

    @Override
    public FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier) {
        boolean isGlobalCache = globalCacheFilter.test(absolutePath);
        VfsState state = isGlobalCache ? globalCacheState : watchedState;
        long versionBefore = state.versionRoot.getVersion(absolutePath);

        boolean notifyListeners = !isGlobalCache;
        FileSystemLocationSnapshot snapshot = snapshotSupplier.get();
        storeIfUnchanged(state, absolutePath, versionBefore, snapshot, notifyListeners);
        return snapshot;
    }

    @Override
    public <T> T storeWithAction(String baseLocation, StoringAction<T> storingAction) {
        boolean isGlobalCache = globalCacheFilter.test(baseLocation);
        VfsState state = isGlobalCache ? globalCacheState : watchedState;
        long versionBefore = state.versionRoot.getVersion(baseLocation);

        boolean notifyListeners = !isGlobalCache;
        return storingAction.snapshot(snapshot -> {
            storeIfUnchanged(state, snapshot.getAbsolutePath(), versionBefore, snapshot, notifyListeners);
            return snapshot;
        });
    }

    private void storeIfUnchanged(VfsState state, String absolutePath, long versionBefore, FileSystemLocationSnapshot snapshot, boolean notifyListeners) {
        // Only update VFS if no changes happened in between.
        // The version in sub-locations may be smaller than the version we queried at the root when using a `StoringAction`.
        if (versionBefore < state.versionRoot.getVersion(absolutePath)) {
            LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
            return;
        }
        AtomicBoolean stored = new AtomicBoolean(false);
        state.underLock(() -> {
            // Check again, now under lock
            if (versionBefore >= state.versionRoot.getVersion(absolutePath)) {
                state.root = notifyListeners
                    ? updateNotifyingListeners(diffListener -> state.root.store(absolutePath, snapshot, diffListener))
                    : state.root.store(absolutePath, snapshot, SnapshotHierarchy.NodeDiffListener.NOOP);
                stored.set(true);
            }
        });
        if (!stored.get()) {
            LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
        }
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        List<String> watchedLocations = new ArrayList<>();
        List<String> globalLocations = new ArrayList<>();
        for (String location : locations) {
            if (globalCacheFilter.test(location)) {
                globalLocations.add(location);
            } else {
                watchedLocations.add(location);
            }
        }
        if (!watchedLocations.isEmpty()) {
            LOGGER.debug("Invalidating VFS paths: {}", watchedLocations);
            doInvalidate(watchedState, watchedLocations, true);
        }
        if (!globalLocations.isEmpty()) {
            LOGGER.debug("Invalidating VFS paths: {}", globalLocations);
            doInvalidate(globalCacheState, globalLocations, false);
        }
    }

    private void doInvalidate(VfsState state, List<String> locations, boolean notifyListeners) {
        state.underLock(() -> {
            SnapshotHierarchy newRoot = state.root;
            VersionHierarchyRoot newVersionRoot = state.versionRoot;
            for (String location : locations) {
                SnapshotHierarchy rootBeforeInvalidation = newRoot;
                newRoot = notifyListeners
                    ? updateNotifyingListeners(diffListener -> rootBeforeInvalidation.invalidate(location, diffListener))
                    : newRoot.invalidate(location, SnapshotHierarchy.NodeDiffListener.NOOP);
                newVersionRoot = newVersionRoot.updateVersion(location);
            }
            state.root = newRoot;
            state.versionRoot = newVersionRoot;
        });
    }

    @Override
    public void invalidateAll() {
        LOGGER.debug("Invalidating the whole VFS");
        watchedState.underLock(() -> {
            watchedState.versionRoot = watchedState.versionRoot.updateVersion(VfsRelativePath.ROOT);
            watchedState.root = updateNotifyingListeners(diffListener -> watchedState.root.invalidate(VfsRelativePath.ROOT, diffListener));
        });
        globalCacheState.underLock(() -> {
            globalCacheState.root = globalCacheState.root.empty();
            globalCacheState.versionRoot = globalCacheState.versionRoot.updateVersion(VfsRelativePath.ROOT);
        });
    }

    /**
     * Runs a single update on a {@link SnapshotHierarchy} and notifies the currently active listeners after the update.
     */
    protected abstract SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction);

    public interface UpdateFunction {
        /**
         * Runs a single update on a {@link SnapshotHierarchy}, notifying the diffListener about changes.
         *
         * @return updated ${@link SnapshotHierarchy}.
         */
        SnapshotHierarchy update(SnapshotHierarchy.NodeDiffListener diffListener);
    }

    /**
     * Holds a VFS snapshot hierarchy together with its version-tracking root and the lock guarding mutations.
     */
    protected static class VfsState {
        private final ReentrantLock lock = new ReentrantLock();
        volatile SnapshotHierarchy root;
        volatile VersionHierarchyRoot versionRoot;

        VfsState(SnapshotHierarchy root, VersionHierarchyRoot versionRoot) {
            this.root = root;
            this.versionRoot = versionRoot;
        }

        public SnapshotHierarchy getRoot() {
            return root;
        }

        void underLock(Runnable runnable) {
            lock.lock();
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        }
    }
}
