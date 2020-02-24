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
import com.google.common.util.concurrent.Striped;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileMetadata;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.snapshot.impl.FileSystemSnapshotFilter;
import org.gradle.internal.vfs.SnapshotHierarchy;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultVirtualFileSystem extends AbstractVirtualFileSystem {
    private final AtomicReference<SnapshotHierarchy> root;
    private final Stat stat;
    private final DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;
    private final StripedProducerGuard<String> producingSnapshots = new StripedProducerGuard<>();

    public DefaultVirtualFileSystem(FileHasher hasher, Interner<String> stringInterner, Stat stat, CaseSensitivity caseSensitivity, String... defaultExcludes) {
        this.stat = stat;
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, defaultExcludes);
        this.hasher = hasher;
        this.root = new AtomicReference<>(DefaultSnapshotHierarchy.empty(caseSensitivity));
    }

    @Override
    public <T> T read(String location, Function<CompleteFileSystemLocationSnapshot, T> visitor) {
        return visitor.apply(readLocation(location));
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        return root.get().getMetadata(location)
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
                FileMetadataSnapshot stat = this.stat.stat(file);
                if (stat.getType() == FileType.Missing) {
                    storeStatForMissingFile(location);
                }
                if (stat.getType() != FileType.RegularFile) {
                    return Optional.empty();
                }
                HashCode hash = producingSnapshots.guardByKey(location,
                    () -> root.get().getSnapshot(location)
                        .orElseGet(() -> {
                            HashCode hashCode = hasher.hash(file, stat.getLength(), stat.getLastModified());
                            RegularFileSnapshot snapshot = new RegularFileSnapshot(location, file.getName(), hashCode, FileMetadata.from(stat));
                            root.updateAndGet(root -> root.store(snapshot.getAbsolutePath(), snapshot));
                            return snapshot;
                        }).getHash());
                return Optional.of(hash);
            })
            .map(visitor);
    }

    private void storeStatForMissingFile(String location) {
        root.updateAndGet(root -> root.store(location, new MissingFileSnapshot(location)));
    }

    @Override
    public void read(String location, SnapshottingFilter filter, Consumer<CompleteFileSystemLocationSnapshot> visitor) {
        if (filter.isEmpty()) {
            visitor.accept(readLocation(location));
        } else {
            FileSystemSnapshot filteredSnapshot = root.get().getSnapshot(location)
                .filter(CompleteFileSystemLocationSnapshot.class::isInstance)
                .map(snapshot -> FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), snapshot))
                .orElseGet(() -> producingSnapshots.guardByKey(location,
                    () -> root.get().getSnapshot(location)
                        .map(snapshot -> FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), snapshot))
                        .orElseGet(() -> {
                            AtomicBoolean hasBeenFiltered = new AtomicBoolean(false);
                            CompleteFileSystemLocationSnapshot snapshot = directorySnapshotter.snapshot(location, filter.getAsDirectoryWalkerPredicate(), hasBeenFiltered);
                            if (!hasBeenFiltered.get()) {
                                root.updateAndGet(root -> root.store(snapshot.getAbsolutePath(), snapshot));
                            }
                            return snapshot;
                        })
                ));

            if (filteredSnapshot instanceof CompleteFileSystemLocationSnapshot) {
                visitor.accept((CompleteFileSystemLocationSnapshot) filteredSnapshot);
            }
        }
    }

    private CompleteFileSystemLocationSnapshot snapshot(String location) {
        File file = new File(location);
        FileMetadataSnapshot stat = this.stat.stat(file);
        switch (stat.getType()) {
            case RegularFile:
                HashCode hash = hasher.hash(file, stat.getLength(), stat.getLastModified());
                RegularFileSnapshot regularFileSnapshot = new RegularFileSnapshot(location, file.getName(), hash, FileMetadata.from(stat));
                root.updateAndGet(root -> root.store(regularFileSnapshot.getAbsolutePath(), regularFileSnapshot));
                return regularFileSnapshot;
            case Missing:
                MissingFileSnapshot missingFileSnapshot = new MissingFileSnapshot(location);
                root.updateAndGet(root -> root.store(missingFileSnapshot.getAbsolutePath(), missingFileSnapshot));
                return missingFileSnapshot;
            case Directory:
                CompleteFileSystemLocationSnapshot directorySnapshot = directorySnapshotter.snapshot(location, null, new AtomicBoolean(false));
                root.updateAndGet(root -> root.store(directorySnapshot.getAbsolutePath(), directorySnapshot));
                return directorySnapshot;
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    SnapshotHierarchy getRoot() {
        return root.get();
    }

    private CompleteFileSystemLocationSnapshot readLocation(String location) {
        return root.get().getSnapshot(location)
            .orElseGet(() -> producingSnapshots.guardByKey(location,
                () -> root.get().getSnapshot(location).orElseGet(() -> snapshot(location)))
            );
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        root.updateAndGet(root -> {
            SnapshotHierarchy result = root;
            for (String location : locations) {
                result = result.invalidate(location);
            }
            return result;
        });
        action.run();
    }

    @Override
    public void invalidateAll() {
        root.updateAndGet(SnapshotHierarchy::empty);
    }

    @Override
    public void updateWithKnownSnapshot(CompleteFileSystemLocationSnapshot snapshot) {
        root.updateAndGet(root -> root.store(snapshot.getAbsolutePath(), snapshot));
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
}
