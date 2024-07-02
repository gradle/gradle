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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.util.concurrent.Striped;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadataAccessor;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.excludes.FileSystemDefaultExcludesListener;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoRunnable;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static org.gradle.internal.snapshot.impl.FileSystemSnapshotFilter.filterSnapshot;

public class DefaultFileSystemAccess implements FileSystemAccess, FileSystemDefaultExcludesListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileSystemAccess.class);

    private final VirtualFileSystem virtualFileSystem;
    private final FileMetadataAccessor stat;
    private final Interner<String> stringInterner;
    private final WriteListener writeListener;
    private final DirectorySnapshotterStatistics.Collector statisticsCollector;
    private ImmutableList<String> defaultExcludes;
    private DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;
    private final StripedProducerGuard<String> producingSnapshots = new StripedProducerGuard<>();

    public DefaultFileSystemAccess(
        FileHasher hasher,
        Interner<String> stringInterner,
        FileMetadataAccessor stat,
        VirtualFileSystem virtualFileSystem,
        WriteListener writeListener,
        DirectorySnapshotterStatistics.Collector statisticsCollector,
        String... defaultExcludes
    ) {
        this.stringInterner = stringInterner;
        this.stat = stat;
        this.writeListener = writeListener;
        this.statisticsCollector = statisticsCollector;
        this.defaultExcludes = ImmutableList.copyOf(defaultExcludes);
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, this.defaultExcludes, statisticsCollector);
        this.hasher = hasher;
        this.virtualFileSystem = virtualFileSystem;
    }

    @Override
    public Optional<HashCode> readRegularFileContentHash(String location) {
        return virtualFileSystem.findMetadata(location)
            .<Optional<FileSystemLocationSnapshot>>flatMap(snapshot -> {
                if (snapshot.getType() != FileType.RegularFile) {
                    return Optional.of(Optional.empty());
                }
                if (snapshot instanceof FileSystemLocationSnapshot) {
                    return Optional.of(Optional.of((FileSystemLocationSnapshot) snapshot));
                }
                return Optional.empty();
            })
            .orElseGet(() -> virtualFileSystem.storeWithAction(location, vfsStorer -> {
                File file = new File(location);
                FileMetadata fileMetadata = this.stat.stat(file);
                switch (fileMetadata.getType()) {
                    case Missing:
                        // For performance reasons, we cache the information about the missing file snapshot.
                        vfsStorer.store(new MissingFileSnapshot(location, fileMetadata.getAccessType()));
                        return Optional.empty();
                    case Directory:
                        return Optional.empty();
                    case RegularFile:
                        // Avoid snapshotting the same location concurrently
                        // This is only a performance optimization for a common scenario; the VFS handles its own concurrency
                        return Optional.of(producingSnapshots.guardByKey(location,
                            () -> virtualFileSystem.findSnapshot(location)
                                .orElseGet(() -> {
                                    HashCode hashCode = hasher.hash(file, fileMetadata.getLength(), fileMetadata.getLastModified());
                                    return vfsStorer.store(new RegularFileSnapshot(location, file.getName(), hashCode, fileMetadata));
                                })));
                    default:
                        throw new IllegalArgumentException("Unknown file type: " + fileMetadata.getType());
                }
            }))
            .map(FileSystemLocationSnapshot::getHash);
    }

    @Override
    public FileSystemLocationSnapshot read(String location) {
        return readSnapshotFromLocation(location,
            Optional::of,
            () -> snapshot(location, SnapshottingFilter.EMPTY)
        ).orElseThrow(() -> new IllegalStateException("Snapshot not found for " + location));
    }

    @Override
    public Optional<FileSystemLocationSnapshot> read(String location, SnapshottingFilter filter) {
        if (filter.isEmpty()) {
            return Optional.of(read(location));
        } else {
            return readSnapshotFromLocation(location,
                storedFilteredSnapshot -> filterSnapshot(filter, storedFilteredSnapshot),
                () -> snapshot(location, filter));
        }
    }

    private <T> T readSnapshotFromLocation(
        String location,
        Function<FileSystemLocationSnapshot, T> snapshotProcessor,
        Supplier<T> readFromDisk
    ) {
        return virtualFileSystem.findSnapshot(location)
            .map(snapshotProcessor)
            // Avoid snapshotting the same location concurrently
            // This is only a performance optimization for a common scenario; the VFS handles its own concurrency
            .orElseGet(() -> producingSnapshots.guardByKey(location,
                () -> virtualFileSystem.findSnapshot(location)
                    .map(snapshotProcessor)
                    .orElseGet(readFromDisk)
            ));
    }

    /**
     * Takes a snapshot of the given location and filters it according to the given filter.
     */
    private Optional<FileSystemLocationSnapshot> snapshot(String location, SnapshottingFilter filter) {
        ImmutableMap<String, FileSystemLocationSnapshot> alreadyStoredSnapshots = virtualFileSystem
            .findRootSnapshotsUnder(location)
            .collect(ImmutableMap.toImmutableMap(
                FileSystemLocationSnapshot::getAbsolutePath,
                Function.identity()
            ));
        FileSystemLocationSnapshot unfilteredSnapshot = alreadyStoredSnapshots.get(location);
        if (unfilteredSnapshot != null) {
            return filterSnapshot(filter, unfilteredSnapshot);
        } else {
            return snapshotAndReuse(location, filter, alreadyStoredSnapshots);
        }
    }

    /**
     * Takes a snapshot of the given location and filters it according to the given filter, reusing previously known snapshots.
     */
    private Optional<FileSystemLocationSnapshot> snapshotAndReuse(String location, SnapshottingFilter filter, ImmutableMap<String, FileSystemLocationSnapshot> previouslyKnownSnapshots) {
        return virtualFileSystem.storeWithAction(location, vfsStorer -> {
            File file = new File(location);
            FileMetadata fileMetadata = this.stat.stat(file);
            FileSystemLocationSnapshot unfilteredSnapshot;
            switch (fileMetadata.getType()) {
                case RegularFile:
                    HashCode hash = hasher.hash(file, fileMetadata.getLength(), fileMetadata.getLastModified());
                    unfilteredSnapshot = vfsStorer.store(new RegularFileSnapshot(location, file.getName(), hash, fileMetadata));
                    break;
                case Missing:
                    unfilteredSnapshot = vfsStorer.store(new MissingFileSnapshot(location, fileMetadata.getAccessType()));
                    break;
                case Directory:
                    // This will capture a filtered snapshot, and only store the captured snapshot in the VFS
                    // if the filter did not filter out anything.
                    return Optional.of(directorySnapshotter.snapshot(
                        location,
                        filter.isEmpty() ? null : filter.getAsDirectoryWalkerPredicate(),
                        previouslyKnownSnapshots,
                        vfsStorer::store));
                default:
                    throw new UnsupportedOperationException();
            }
            return filterSnapshot(filter, unfilteredSnapshot);
        });
    }

    @Override
    public void invalidate(Iterable<String> locations) {
        writeListener.locationsWritten(locations);
        virtualFileSystem.invalidate(locations);
    }

    @Override
    public void write(Iterable<String> locations, IoRunnable action) throws IOException {
        invalidate(locations);
        action.run();
    }

    @Override
    public void record(FileSystemLocationSnapshot snapshot) {
        virtualFileSystem.store(snapshot.getAbsolutePath(), () -> snapshot);
    }

    @Override
    public void moveAtomically(String sourceLocation, String targetLocation) throws IOException {
        FileSystemLocationSnapshot sourceSnapshot = read(sourceLocation);
        write(ImmutableList.of(sourceLocation, targetLocation), () -> {
            Files.move(Paths.get(sourceLocation), Paths.get(targetLocation), ATOMIC_MOVE);
            sourceSnapshot.relocate(targetLocation, stringInterner)
                .ifPresent(this::record);
        });
    }

    @Override
    public void onDefaultExcludesChanged(List<String> excludes) {
        ImmutableList<String> newDefaultExcludes = ImmutableList.copyOf(excludes);
        if (!defaultExcludes.equals(newDefaultExcludes)) {
            LOGGER.debug("Default excludes changes from {} to {}", defaultExcludes, newDefaultExcludes);
            defaultExcludes = newDefaultExcludes;
            directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, newDefaultExcludes, statisticsCollector);
            virtualFileSystem.invalidateAll();
        }
    }

    private static class StripedProducerGuard<T> {
        private final Striped<Lock> locks = Striped.lock(Runtime.getRuntime().availableProcessors() * 4);

        public <V> V guardByKey(T key, Supplier<V> supplier) {
            Lock lock = locks.get(key);
            lock.lock();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }
}
