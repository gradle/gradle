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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.common.util.concurrent.Striped;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.snapshot.impl.FileSystemSnapshotFilter;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultFileSystemAccess implements FileSystemAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileSystemAccess.class);

    private final VirtualFileSystem virtualFileSystem;
    private final Stat stat;
    private final Interner<String> stringInterner;
    private final WriteListener writeListener;
    private ImmutableList<String> defaultExcludes;
    private DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;
    private final StripedProducerGuard<String> producingSnapshots = new StripedProducerGuard<>();

    public DefaultFileSystemAccess(
        FileHasher hasher,
        Interner<String> stringInterner,
        Stat stat,
        VirtualFileSystem virtualFileSystem,
        WriteListener writeListener,
        String... defaultExcludes
    ) {
        this.stringInterner = stringInterner;
        this.stat = stat;
        this.writeListener = writeListener;
        this.defaultExcludes = ImmutableList.copyOf(defaultExcludes);
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, this.defaultExcludes);
        this.hasher = hasher;
        this.virtualFileSystem = virtualFileSystem;
    }

    @Override
    public <T> T read(String location, Function<CompleteFileSystemLocationSnapshot, T> visitor) {
        return visitor.apply(readLocation(location));
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        return virtualFileSystem.getMetadata(location)
            .<Optional<HashCode>>flatMap(snapshot -> {
                if (snapshot.getType() != FileType.RegularFile) {
                    return Optional.of(Optional.empty());
                }
                if (snapshot instanceof CompleteFileSystemLocationSnapshot) {
                    return Optional.of(Optional.of(((CompleteFileSystemLocationSnapshot) snapshot).getHash()));
                }
                return Optional.empty();
            })
            .orElseGet(() -> {
                File file = new File(location);
                FileMetadata fileMetadata = this.stat.stat(file);
                if (fileMetadata.getType() == FileType.Missing) {
                    storeMetadataForMissingFile(location, fileMetadata.getAccessType());
                }
                if (fileMetadata.getType() != FileType.RegularFile) {
                    return Optional.empty();
                }
                HashCode hash = producingSnapshots.guardByKey(location,
                    () -> virtualFileSystem.getSnapshot(location)
                        .orElseGet(() -> {
                            HashCode hashCode = hasher.hash(file, fileMetadata.getLength(), fileMetadata.getLastModified());
                            RegularFileSnapshot snapshot = new RegularFileSnapshot(location, file.getName(), hashCode, fileMetadata);
                            virtualFileSystem.store(snapshot.getAbsolutePath(), snapshot);
                            return snapshot;
                        }).getHash());
                return Optional.of(hash);
            })
            .map(visitor);
    }

    private void storeMetadataForMissingFile(String location, AccessType accessType) {
        virtualFileSystem.store(location, new MissingFileSnapshot(location, accessType));
    }

    @Override
    public void read(String location, SnapshottingFilter filter, Consumer<CompleteFileSystemLocationSnapshot> visitor) {
        if (filter.isEmpty()) {
            visitor.accept(readLocation(location));
        } else {
            FileSystemSnapshot filteredSnapshot = readSnapshotFromLocation(location,
                snapshot -> FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), snapshot),
                () -> {
                    CompleteFileSystemLocationSnapshot snapshot = snapshot(location, filter);
                    return snapshot.getType() == FileType.Directory
                        // Directory snapshots have been filtered while walking the file system
                        ? snapshot
                        : FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), snapshot);
                });

            if (filteredSnapshot instanceof CompleteFileSystemLocationSnapshot) {
                visitor.accept((CompleteFileSystemLocationSnapshot) filteredSnapshot);
            }
        }
    }

    private CompleteFileSystemLocationSnapshot snapshot(String location, SnapshottingFilter filter) {
        File file = new File(location);
        FileMetadata fileMetadata = this.stat.stat(file);
        switch (fileMetadata.getType()) {
            case RegularFile:
                HashCode hash = hasher.hash(file, fileMetadata.getLength(), fileMetadata.getLastModified());
                RegularFileSnapshot regularFileSnapshot = new RegularFileSnapshot(location, file.getName(), hash, fileMetadata);
                virtualFileSystem.store(regularFileSnapshot.getAbsolutePath(), regularFileSnapshot);
                return regularFileSnapshot;
            case Missing:
                MissingFileSnapshot missingFileSnapshot = new MissingFileSnapshot(location, fileMetadata.getAccessType());
                virtualFileSystem.store(missingFileSnapshot.getAbsolutePath(), missingFileSnapshot);
                return missingFileSnapshot;
            case Directory:
                AtomicBoolean hasBeenFiltered = new AtomicBoolean(false);
                CompleteFileSystemLocationSnapshot directorySnapshot = directorySnapshotter.snapshot(location, filter.isEmpty() ? null : filter.getAsDirectoryWalkerPredicate(), hasBeenFiltered);
                if (!hasBeenFiltered.get()) {
                    virtualFileSystem.store(directorySnapshot.getAbsolutePath(), directorySnapshot);
                }
                return directorySnapshot;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private CompleteFileSystemLocationSnapshot readLocation(String location) {
        return readSnapshotFromLocation(location, () -> snapshot(location, SnapshottingFilter.EMPTY));
    }

    private CompleteFileSystemLocationSnapshot readSnapshotFromLocation(
        String location,
        Supplier<CompleteFileSystemLocationSnapshot> readFromDisk
    ) {
        return readSnapshotFromLocation(
            location,
            Function.identity(),
            readFromDisk
        );
    }

    private <T> T readSnapshotFromLocation(
        String location,
        Function<CompleteFileSystemLocationSnapshot, T> snapshotProcessor,
        Supplier<T> readFromDisk
    ) {
        return virtualFileSystem.getSnapshot(location)
            .map(snapshotProcessor)
            // Avoid snapshotting the same location at the same time
            .orElseGet(() -> producingSnapshots.guardByKey(location,
                () -> virtualFileSystem.getSnapshot(location)
                    .map(snapshotProcessor)
                    .orElseGet(readFromDisk)
            ));
    }

    @Override
    public void write(Iterable<String> locations, Runnable action) {
        writeListener.locationsWritten(locations);
        virtualFileSystem.invalidate(locations);
        action.run();
    }

    @Override
    public void record(CompleteFileSystemLocationSnapshot snapshot) {
        virtualFileSystem.store(snapshot.getAbsolutePath(), snapshot);
    }

    private static class StripedProducerGuard<T> {
        private final Striped<Lock> locks = Striped.lock(Runtime.getRuntime().availableProcessors() * 4);

        public <V> V guardByKey(T key, Supplier<V> supplier) {
            Lock lock = locks.get(key);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }

    public void updateDefaultExcludes(String... newDefaultExcludesArgs) {
        ImmutableList<String> newDefaultExcludes = ImmutableList.copyOf(newDefaultExcludesArgs);
        if (!defaultExcludes.equals(newDefaultExcludes)) {
            LOGGER.debug("Default excludes changes from {} to {}", defaultExcludes, newDefaultExcludes);
            defaultExcludes = newDefaultExcludes;
            directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, newDefaultExcludes);
            virtualFileSystem.invalidateAll();
        }
    }
}
