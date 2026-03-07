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

import com.google.common.collect.Iterables;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.VfsRelativePath;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVirtualFileSystem.class);

    /**
     * State for project files and other watched locations. Subject to file-event invalidations.
     */
    private final AtomicReference<VfsState> watchedState;

    /**
     * State for global cache paths (Gradle user home caches). These are Gradle-managed and
     * never invalidated by file events, so they can be updated independently without
     * contending with watched-path stores and invalidations.
     */
    private final AtomicReference<VfsState> globalCacheState;

    private final Predicate<String> globalCacheFilter;

    protected AbstractVirtualFileSystem(SnapshotHierarchy root, Predicate<String> globalCacheFilter) {
        VersionHierarchyRoot initialVersionRoot = VersionHierarchyRoot.empty(0, root.getCaseSensitivity());
        this.watchedState = new AtomicReference<>(new VfsState(root, initialVersionRoot));
        this.globalCacheState = new AtomicReference<>(new VfsState(root.empty(), initialVersionRoot));
        this.globalCacheFilter = globalCacheFilter;
    }

    private AtomicReference<VfsState> stateFor(String absolutePath) {
        return globalCacheFilter.test(absolutePath) ? globalCacheState : watchedState;
    }

    /**
     * Returns the current root of the watched state.
     * The watcher infrastructure only operates on watched paths, never global cache paths.
     */
    protected SnapshotHierarchy currentRoot() {
        return watchedState.get().root;
    }

    /**
     * Replaces the watched root and clears the global cache state.
     * Called when disabling watching or after errors — conservative clear of all state.
     */
    protected void replaceRoot(SnapshotHierarchy newRoot) {
        while (true) {
            VfsState current = watchedState.get();
            if (watchedState.compareAndSet(current, new VfsState(newRoot, current.versionRoot))) {
                break;
            }
        }
        while (true) {
            VfsState current = globalCacheState.get();
            if (globalCacheState.compareAndSet(current, new VfsState(newRoot.empty(), current.versionRoot))) {
                return;
            }
        }
    }

    @Override
    public Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath) {
        return stateFor(absolutePath).get().root.findSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> findMetadata(String absolutePath) {
        return stateFor(absolutePath).get().root.findMetadata(absolutePath);
    }

    @Override
    public Stream<FileSystemLocationSnapshot> findRootSnapshotsUnder(String absolutePath) {
        return stateFor(absolutePath).get().root.rootSnapshotsUnder(absolutePath);
    }

    @Override
    public FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier) {
        AtomicReference<VfsState> stateRef = stateFor(absolutePath);
        long versionBefore = stateRef.get().versionRoot.getVersion(absolutePath);
        FileSystemLocationSnapshot snapshot = snapshotSupplier.get();
        storeIfUnchanged(stateRef, absolutePath, versionBefore, snapshot);
        return snapshot;
    }

    @Override
    public <T> T storeWithAction(String baseLocation, StoringAction<T> storingAction) {
        AtomicReference<VfsState> stateRef = stateFor(baseLocation);
        long versionBefore = stateRef.get().versionRoot.getVersion(baseLocation);
        return storingAction.snapshot(snapshot -> {
            storeIfUnchanged(stateRef, snapshot.getAbsolutePath(), versionBefore, snapshot);
            return snapshot;
        });
    }

    private void storeIfUnchanged(AtomicReference<VfsState> stateRef, String absolutePath, long versionBefore, FileSystemLocationSnapshot snapshot) {
        boolean isGlobalCache = stateRef == globalCacheState;
        // The Lock-Free CAS Loop
        while (true) {
            VfsState currentState = stateRef.get();
            long versionAfter = currentState.versionRoot.getVersion(absolutePath);

            if (versionBefore < versionAfter) {
                LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
                return;
            }

            // Global cache paths are never watched — skip buffering and watcher notification.
            if (isGlobalCache) {
                SnapshotHierarchy newRoot = currentState.root.store(absolutePath, snapshot, SnapshotHierarchy.NodeDiffListener.NOOP);
                if (stateRef.compareAndSet(currentState, new VfsState(newRoot, currentState.versionRoot))) {
                    return;
                }
                continue;
            }

            BufferingDiffListener bufferListener = new BufferingDiffListener();
            SnapshotHierarchy newRoot = currentState.root.store(absolutePath, snapshot, bufferListener);

            VfsState newState = new VfsState(newRoot, currentState.versionRoot);
            if (stateRef.compareAndSet(currentState, newState)) {
                updateNotifyingListeners(diffListener -> {
                    bufferListener.flushToRealListener(diffListener);
                    return newState.root;
                });
                return;
            }
        }
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        if (Iterables.isEmpty(locations)) {
            return;
        }

        LOGGER.debug("Invalidating VFS paths: {}", locations);

        // Partition locations by state. In practice all file-event invalidations go to
        // watchedState, but route correctly in case global cache paths appear.
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
            doInvalidate(watchedState, watchedLocations, UnaryOperator.identity());
        }
        if (!globalLocations.isEmpty()) {
            doInvalidate(globalCacheState, globalLocations, UnaryOperator.identity());
        }
    }

    @Override
    public void invalidateAll() {
        LOGGER.debug("Invalidating the whole VFS");
        doInvalidate(watchedState, Collections.singletonList(VfsRelativePath.ROOT), UnaryOperator.identity());
        doInvalidate(globalCacheState, Collections.singletonList(VfsRelativePath.ROOT), UnaryOperator.identity());
    }

    /**
     * Invalidates paths from the VFS without notifying the file watcher.
     * Use this when the file watcher state has already been updated separately,
     * and triggering another watcher notification cycle would be redundant and costly.
     * In practice these paths are always in the watched state.
     */
    protected void invalidateWithoutNotifyingWatcher(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        while (true) {
            VfsState currentState = watchedState.get();
            SnapshotHierarchy newRoot = currentState.root;
            VersionHierarchyRoot newVersionRoot = currentState.versionRoot;
            for (String path : paths) {
                newRoot = newRoot.invalidate(path, SnapshotHierarchy.NodeDiffListener.NOOP);
                newVersionRoot = newVersionRoot.updateVersion(path);
            }
            if (watchedState.compareAndSet(currentState, new VfsState(newRoot, newVersionRoot))) {
                return;
            }
        }
    }

    /**
     * Invalidates multiple paths wrapping the diff listener via {@code listenerWrapper} so subclasses can decorate it (e.g. for logging).
     */
    protected void invalidateAndNotify(Iterable<String> absolutePaths, UnaryOperator<SnapshotHierarchy.NodeDiffListener> listenerWrapper) {
        // In practice all file-event invalidations are in the watched state.
        doInvalidate(watchedState, absolutePaths, listenerWrapper);
    }

    private void doInvalidate(AtomicReference<VfsState> stateRef, Iterable<String> locations, UnaryOperator<SnapshotHierarchy.NodeDiffListener> listenerWrapper) {
        // Global cache paths are never watched — skip watcher notification.
        boolean isGlobalCache = stateRef == globalCacheState;
        while (true) {
            VfsState currentState = stateRef.get();

            SnapshotHierarchy currentRoot = currentState.root;
            VersionHierarchyRoot currentVersionRoot = currentState.versionRoot;

            if (isGlobalCache) {
                for (String location : locations) {
                    currentRoot = currentRoot.invalidate(location, SnapshotHierarchy.NodeDiffListener.NOOP);
                    currentVersionRoot = currentVersionRoot.updateVersion(location);
                }
                if (stateRef.compareAndSet(currentState, new VfsState(currentRoot, currentVersionRoot))) {
                    return;
                }
                continue;
            }

            // Apply all invalidations to the current state in memory,
            // accumulating all diff events in a single listener across all locations.
            BufferingDiffListener bufferListener = new BufferingDiffListener();
            for (String location : locations) {
                currentRoot = currentRoot.invalidate(location, bufferListener);
                currentVersionRoot = currentVersionRoot.updateVersion(location);
            }

            VfsState newState = new VfsState(currentRoot, currentVersionRoot);

            if (stateRef.compareAndSet(currentState, newState)) {
                updateNotifyingListeners(diffListener -> {
                    bufferListener.flushToRealListener(listenerWrapper.apply(diffListener));
                    return newState.root;
                });
                return;
            }
        }
    }

    protected abstract SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction);

    public interface UpdateFunction {
        SnapshotHierarchy update(SnapshotHierarchy.NodeDiffListener diffListener);
    }

    private static class VfsState {
        final SnapshotHierarchy root;
        final VersionHierarchyRoot versionRoot;

        VfsState(SnapshotHierarchy root, VersionHierarchyRoot versionRoot) {
            this.root = root;
            this.versionRoot = versionRoot;
        }
    }

    private static class BufferingDiffListener implements SnapshotHierarchy.NodeDiffListener {

        // Stores the sequence of events as executable commands
        private final List<Consumer<SnapshotHierarchy.NodeDiffListener>> bufferedEvents = new ArrayList<>();

        @Override
        public void nodeAdded(FileSystemNode node) {
            // We don't fire the event; we just record what *would* happen
            bufferedEvents.add(realListener -> realListener.nodeAdded(node));
        }

        @Override
        public void nodeRemoved(FileSystemNode node) {
            bufferedEvents.add(realListener -> realListener.nodeRemoved(node));
        }

        public void flushToRealListener(SnapshotHierarchy.NodeDiffListener realListener) {
            if (bufferedEvents.isEmpty()) {
                return;
            }
            for (Consumer<SnapshotHierarchy.NodeDiffListener> event : bufferedEvents) {
                event.accept(realListener);
            }
        }
    }
}
