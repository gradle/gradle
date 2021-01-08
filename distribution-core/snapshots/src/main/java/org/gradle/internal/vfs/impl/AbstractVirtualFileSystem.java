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

import java.util.Optional;

public abstract class AbstractVirtualFileSystem implements VirtualFileSystem {

    protected final VfsRootReference rootReference;

    protected AbstractVirtualFileSystem(VfsRootReference rootReference) {
        this.rootReference = rootReference;
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String absolutePath) {
        return rootReference.getRoot().getSnapshot(absolutePath);
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        return rootReference.getRoot().getMetadata(absolutePath);
    }

    @Override
    public void store(String absolutePath, FileSystemLocationSnapshot snapshot) {
        rootReference.update(root -> updateNotifyingListeners(diffListener -> root.store(absolutePath, snapshot, diffListener)));
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        rootReference.update(root -> {
            SnapshotHierarchy result = root;
            for (String location : locations) {
                SnapshotHierarchy currentRoot = result;
                result = updateNotifyingListeners(diffListener -> currentRoot.invalidate(location, diffListener));
            }
            return result;
        });
    }

    @Override
    public void invalidateAll() {
        rootReference.update(root -> updateNotifyingListeners(diffListener -> {
            root.visitSnapshotRoots(diffListener::nodeRemoved);
            return root.empty();
        }));
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
