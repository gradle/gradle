/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror;
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter;
import org.gradle.internal.vfs.VirtualFileSystem;

import java.io.File;
import java.util.Optional;
import java.util.function.Function;

public class FileSystemSnapshotterVirtualFileSystem implements VirtualFileSystem {

    private final FileSystemSnapshotter snapshotter;
    private final DefaultFileSystemMirror mirror;

    public FileSystemSnapshotterVirtualFileSystem(FileHasher hasher, Interner<String> stringInterner, Stat stat, String... defaultExcludes) {
        this.mirror = new DefaultFileSystemMirror();
        this.snapshotter = new DefaultFileSystemSnapshotter(hasher, stringInterner, stat, mirror, defaultExcludes);
    }

    @Override
    public void read(String location, FileSystemSnapshotVisitor visitor) {
        maybeShortCircuitVisitor(snapshotter.snapshot(new File(location)), visitor);
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        HashCode hashCode = snapshotter.getRegularFileContentHash(new File(location));
        return Optional.ofNullable(hashCode).map(visitor);
    }

    @Override
    public void read(String location, SnapshottingFilter filter, FileSystemSnapshotVisitor visitor) {
        FileSystemSnapshot snapshot = snapshotter.snapshotDirectoryTree(new File(location), filter);
        if (snapshot instanceof FileSystemLocationSnapshot) {
            maybeShortCircuitVisitor((FileSystemLocationSnapshot) snapshot, visitor);
        } else {
            snapshot.accept(visitor);
        }
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        mirror.invalidate(locations);
        action.run();
    }

    @Override
    public void invalidateAll() {
        mirror.invalidateAll();
    }

    @Override
    public void updateWithKnownSnapshot(String location, FileSystemLocationSnapshot snapshot) {
        determineMetadata(snapshot.getType())
            .ifPresent(metadata -> mirror.putMetadata(snapshot.getAbsolutePath(), metadata));
        mirror.putSnapshot(snapshot);
    }

    private Optional<FileMetadataSnapshot> determineMetadata(FileType fileType) {
        switch (fileType) {
            case RegularFile:
                return Optional.empty();
            case Directory:
                return Optional.of(DefaultFileMetadata.directory());
            case Missing:
                return Optional.of(DefaultFileMetadata.missing());
            default:
                throw new AssertionError("Unknown file type: " + fileType);
        }
    }

    private void maybeShortCircuitVisitor(FileSystemLocationSnapshot snapshot, FileSystemSnapshotVisitor visitor) {
        if (visitor instanceof MerkleDirectorySnapshotBuilder) {
            ((MerkleDirectorySnapshotBuilder) visitor).resultKnown(snapshot);
        } else {
            snapshot.accept(visitor);
        }
    }
}
