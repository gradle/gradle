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
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVirtualFileSystem.class);

    private final AtomicReference<VfsState> state;

    protected AbstractVirtualFileSystem(SnapshotHierarchy root) {
        VersionHierarchyRoot initialVersionRoot = VersionHierarchyRoot.empty(0, root.getCaseSensitivity());
        this.state = new AtomicReference<>(new VfsState(root, initialVersionRoot));
    }

    protected SnapshotHierarchy currentRoot() {
        return state.get().root;
    }

    protected void replaceRoot(SnapshotHierarchy newRoot) {
        while (true) {
            VfsState currentState = state.get();
            VfsState newState = new VfsState(newRoot, currentState.versionRoot);

            if (state.compareAndSet(currentState, newState)) {
                return;
            }
        }
    }

    @Override
    public Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath) {
        return state.get().root.findSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> findMetadata(String absolutePath) {
        return state.get().root.findMetadata(absolutePath);
    }

    @Override
    public Stream<FileSystemLocationSnapshot> findRootSnapshotsUnder(String absolutePath) {
        return state.get().root.rootSnapshotsUnder(absolutePath);
    }

    @Override
    public FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier) {
        long versionBefore = state.get().versionRoot.getVersion(absolutePath);
        FileSystemLocationSnapshot snapshot = snapshotSupplier.get();
        storeIfUnchanged(absolutePath, versionBefore, snapshot);
        return snapshot;
    }

    @Override
    public <T> T storeWithAction(String baseLocation, StoringAction<T> storingAction) {
        long versionBefore = state.get().versionRoot.getVersion(baseLocation);
        return storingAction.snapshot(snapshot -> {
            storeIfUnchanged(snapshot.getAbsolutePath(), versionBefore, snapshot);
            return snapshot;
        });
    }

    private void storeIfUnchanged(String absolutePath, long versionBefore, FileSystemLocationSnapshot snapshot) {
        // The Lock-Free CAS Loop
        while (true) {
            VfsState currentState = state.get();
            long versionAfter = currentState.versionRoot.getVersion(absolutePath);

            if (versionBefore < versionAfter) {
                LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
                return;
            }

            BufferingDiffListener bufferListener = new BufferingDiffListener();
            SnapshotHierarchy newRoot = currentState.root.store(absolutePath, snapshot, bufferListener);

            VfsState newState = new VfsState(newRoot, currentState.versionRoot);
            if (state.compareAndSet(currentState, newState)) {
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
        LOGGER.debug("Invalidating VFS paths: {}", locations);
        if (Iterables.isEmpty(locations)) {
            return;
        }

        while (true) {
            VfsState currentState = state.get();

            SnapshotHierarchy currentRoot = currentState.root;
            VersionHierarchyRoot currentVersionRoot = currentState.versionRoot;

            // Apply all invalidations to the current state in memory,
            // accumulating all diff events in a single listener across all locations.
            BufferingDiffListener bufferListener = new BufferingDiffListener();
            for (String location : locations) {
                final SnapshotHierarchy root = currentRoot;
                currentRoot = root.invalidate(location, bufferListener);
                currentVersionRoot = currentVersionRoot.updateVersion(location);
            }

            VfsState newState = new VfsState(currentRoot, currentVersionRoot);

            if (state.compareAndSet(currentState, newState)) {
                updateNotifyingListeners(diffListener -> {
                    bufferListener.flushToRealListener(diffListener);
                    return newState.root;
                });
                return;
            }
        }
    }

    @Override
    public void invalidateAll() {
        LOGGER.debug("Invalidating the whole VFS");
        invalidate(Collections.singletonList(VfsRelativePath.ROOT));
    }

    /**
     * Invalidates a single path using a CAS loop, wrapping the diff listener via {@code listenerWrapper}
     * so subclasses can decorate it (e.g. for logging) before it reaches {@link #updateNotifyingListeners}.
     */
    protected void invalidateAndNotify(String absolutePath, UnaryOperator<SnapshotHierarchy.NodeDiffListener> listenerWrapper) {
        while (true) {
            VfsState currentState = state.get();
            BufferingDiffListener bufferListener = new BufferingDiffListener();
            SnapshotHierarchy newRoot = currentState.root.invalidate(absolutePath, bufferListener);
            VfsState newState = new VfsState(newRoot, currentState.versionRoot.updateVersion(absolutePath));
            if (state.compareAndSet(currentState, newState)) {
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
