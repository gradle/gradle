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
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractVirtualFileSystem.class);

    protected final VfsRootReference rootReference;
    private volatile VersionHierarchyRoot versionHierarchyRoot;
    private long currentVersion;

    protected AbstractVirtualFileSystem(VfsRootReference rootReference) {
        this.rootReference = rootReference;
        this.versionHierarchyRoot = VersionHierarchyRoot.empty(currentVersion, rootReference.getRoot().getCaseSensitivity());
    }

    @Override
    public Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath) {
        return rootReference.getRoot().findSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> findMetadata(String absolutePath) {
        return rootReference.getRoot().findMetadata(absolutePath);
    }

    @Override
    public FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier) {
        long versionBefore = versionHierarchyRoot.getVersionFor(absolutePath);
        FileSystemLocationSnapshot snapshot = snapshotSupplier.get();
        updateCheckingVersion(absolutePath, versionBefore, snapshot);
        return snapshot;
    }

    @Override
    public FileSystemLocationSnapshot store(String baseLocation, StoringAction storingAction) {
        long versionBefore = versionHierarchyRoot.getVersionFor(baseLocation);
        return storingAction.snapshot(snapshot -> updateCheckingVersion(snapshot.getAbsolutePath(), versionBefore, snapshot));
    }

    private void updateCheckingVersion(String absolutePath, long versionBefore, FileSystemLocationSnapshot snapshot) {
        long versionAfter = versionHierarchyRoot.getVersionFor(absolutePath);
        // Only update VFS if no changes happened in between
        if (versionBefore == versionAfter) {
            rootReference.update(root -> updateNotifyingListeners(diffListener -> root.store(absolutePath, snapshot, diffListener)));
        } else {
            LOGGER.debug("Changes to the virtual file system happened while snapshotting '{}', not storing resulting snapshot", absolutePath);
        }
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        LOGGER.debug("Invalidating VFS paths: {}", locations);
        rootReference.update(root -> {
            SnapshotHierarchy result = root;
            VersionHierarchyRoot newVersionHierarchyRoot = versionHierarchyRoot;
            long nextVersion = ++currentVersion;
            for (String location : locations) {
                SnapshotHierarchy currentRoot = result;
                result = updateNotifyingListeners(diffListener -> currentRoot.invalidate(location, diffListener));
                newVersionHierarchyRoot = newVersionHierarchyRoot.increaseVersion(location, nextVersion);
            }
            versionHierarchyRoot = newVersionHierarchyRoot;
            return result;
        });
    }

    @Override
    public void invalidateAll() {
        LOGGER.debug("Invalidating the whole VFS");
        rootReference.update(root -> {
            currentVersion = 0;
            versionHierarchyRoot = VersionHierarchyRoot.empty(currentVersion, root.getCaseSensitivity());
            return updateNotifyingListeners(diffListener -> {
                root.rootSnapshots()
                    .forEach(diffListener::nodeRemoved);
                return root.empty();
            });
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
}
