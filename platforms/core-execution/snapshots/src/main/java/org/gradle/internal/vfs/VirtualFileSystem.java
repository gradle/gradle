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

package org.gradle.internal.vfs;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MetadataSnapshot;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface VirtualFileSystem {

    /**
     * Returns the snapshot stored at the absolute path if it exists in the VFS.
     */
    Optional<FileSystemLocationSnapshot> findSnapshot(String absolutePath);

    /**
     * Returns the metadata stored at the absolute path if it exists.
     */
    Optional<MetadataSnapshot> findMetadata(String absolutePath);

    /**
     * Returns all root snapshots in the hierarchy below {@code absolutePath}.
     */
    Stream<FileSystemLocationSnapshot> findRootSnapshotsUnder(String absolutePath);

    /**
     * Snapshots and stores the result in the VFS.
     *
     * If the snapshotted location is invalidated while snapshotting,
     * then the snapshot is not stored in the VFS to avoid inconsistent state.
     */
    FileSystemLocationSnapshot store(String absolutePath, Supplier<FileSystemLocationSnapshot> snapshotSupplier);


    /**
     * Snapshots via a {@link StoringAction} and stores the result in the VFS.
     *
     * If the snapshotted location is invalidated while snapshotting,
     * then the snapshot is not stored in the VFS to avoid inconsistent state.
     */
    <T> T store(String baseLocation, StoringAction<T> storingAction);

    /**
     * Snapshotting action which produces possibly more than one snapshot.
     *
     * For example when snapshotting a filtered directory, the snapshots for complete subdirectories
     * would be reported here when they are found.
     */
    interface StoringAction<T> {
        T snapshot(VfsStorer snapshot);
    }

    interface VfsStorer {
        FileSystemLocationSnapshot store(FileSystemLocationSnapshot snapshot);
    }

    /**
     * Removes any information at the absolute paths from the VFS.
     */
    void invalidate(Iterable<String> locations);

    /**
     * Removes any information from the VFS.
     */
    void invalidateAll();

}
