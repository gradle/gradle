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

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVirtualFileSystem.class);

    private final ReentrantLock updateLock = new ReentrantLock();

    // Mutable state, changes need to be guarded by updateLock
    protected volatile SnapshotHierarchy root;
    private volatile VersionHierarchyRoot versionHierarchyRoot;

    protected AbstractVirtualFileSystem(SnapshotHierarchy root) {
        this.root = root;
        this.versionHierarchyRoot = VersionHierarchyRoot.empty(0, root.getCaseSensitivity());
    }

    protected void underLock(Runnable runnable) {
        updateLock.lock();
        try {
            runnable.run();
        } finally {
            updateLock.unlock();
        }
    }

    protected void updateRootUnderLock(UnaryOperator<SnapshotHierarchy> updateFunction) {
        underLock(() -> {
            SnapshotHierarchy currentRoot = root;
            root = updateFunction.apply(currentRoot);
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath) {
        return root.findSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> findMetadata(String absolutePath) {
        return root.findMetadata(absolutePath);
    }

    @Override
    public FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier) {
        long versionBefore = versionHierarchyRoot.getVersion(absolutePath);
        FileSystemLocationSnapshot snapshot = snapshotSupplier.get();
        storeIfUnchanged(absolutePath, versionBefore, snapshot);
        return snapshot;
    }

    @Override
    public <T> T store(String baseLocation, StoringAction<T> storingAction) {
        long versionBefore = versionHierarchyRoot.getVersion(baseLocation);
        return storingAction.snapshot(snapshot -> {
            storeIfUnchanged(snapshot.getAbsolutePath(), versionBefore, snapshot);
            return snapshot;
        });
    }

    private void storeIfUnchanged(String absolutePath, long versionBefore, FileSystemLocationSnapshot snapshot) {
        long versionAfter = versionHierarchyRoot.getVersion(absolutePath);
        // Only update VFS if no changes happened in between
        // The version in sub-locations may be smaller than the version we queried at the root when using a `StoringAction`.
        if (versionBefore >= versionAfter) {
            updateRootUnderLock(root -> updateNotifyingListeners(diffListener -> root.store(absolutePath, snapshot, diffListener)));
        } else {
            LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
        }
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        LOGGER.debug("Invalidating VFS paths: {}", locations);
        updateRootUnderLock(root -> {
            SnapshotHierarchy result = root;
            VersionHierarchyRoot newVersionHierarchyRoot = versionHierarchyRoot;
            for (String location : locations) {
                SnapshotHierarchy currentRoot = result;
                result = updateNotifyingListeners(diffListener -> currentRoot.invalidate(location, diffListener));
                newVersionHierarchyRoot = newVersionHierarchyRoot.updateVersion(location);
            }
            versionHierarchyRoot = newVersionHierarchyRoot;
            return result;
        });
    }

    @Override
    public void invalidateAll() {
        LOGGER.debug("Invalidating the whole VFS");
        invalidate(Collections.singletonList(VfsRelativePath.ROOT));
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
}
