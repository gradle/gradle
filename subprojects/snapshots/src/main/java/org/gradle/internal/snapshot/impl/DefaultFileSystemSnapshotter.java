/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.Interner;
import com.google.common.util.concurrent.Striped;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemMirror;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotBuilder;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Responsible for snapshotting various aspects of the file system.
 *
 * Currently logic and state are split between this class and {@link FileSystemMirror}, as there are several instances of this class created in different scopes. This introduces some inefficiencies
 * that could be improved by shuffling this relationship around.
 *
 * The implementations attempt to do 2 things: avoid doing the same work in parallel (e.g. scanning the same directory from multiple threads, and avoid doing work where the result is almost certainly
 * the same as before (e.g. don't scan the output directory of a task a bunch of times).
 *
 * The implementations are currently intentionally very, very simple, and so there are a number of ways in which they can be made much more efficient. This can happen over time.
 */
public class DefaultFileSystemSnapshotter implements FileSystemSnapshotter {
    private final FileHasher hasher;
    private final Interner<String> stringInterner;
    private final Stat stat;
    private final FileSystemMirror fileSystemMirror;
    private final StripedProducerGuard<String> producingSnapshots = new StripedProducerGuard<>();
    private final DirectorySnapshotter directorySnapshotter;

    public DefaultFileSystemSnapshotter(FileHasher hasher, Interner<String> stringInterner, Stat stat, FileSystemMirror fileSystemMirror, String... defaultExcludes) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.stat = stat;
        this.fileSystemMirror = fileSystemMirror;
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, defaultExcludes);
    }

    @Override
    public HashCode getRegularFileContentHash(final File file) {
        final String absolutePath = file.getAbsolutePath();
        FileMetadataSnapshot metadata = fileSystemMirror.getMetadata(absolutePath);
        if (metadata != null) {
            if (metadata.getType() != FileType.RegularFile) {
                return null;
            }
            FileSystemLocationSnapshot snapshot = fileSystemMirror.getSnapshot(absolutePath);
            if (snapshot != null) {
                return snapshot.getHash();
            }
        }
        return producingSnapshots.guardByKey(absolutePath, new Supplier<HashCode>() {
            @Nullable
            @Override
            public HashCode get() {
                InternableString internableAbsolutePath = new InternableString(absolutePath);
                FileMetadataSnapshot metadata = statAndCache(internableAbsolutePath, file);
                if (metadata.getType() != FileType.RegularFile) {
                    return null;
                }
                FileSystemLocationSnapshot snapshot = snapshotAndCache(internableAbsolutePath, file, metadata, null);
                return snapshot.getHash();
            }
        });
    }

    @Override
    public FileSystemLocationSnapshot snapshot(final File file) {
        final String absolutePath = file.getAbsolutePath();
        FileSystemLocationSnapshot result = fileSystemMirror.getSnapshot(absolutePath);
        if (result == null) {
            result = producingSnapshots.guardByKey(absolutePath, new Supplier<FileSystemLocationSnapshot>() {
                @Override
                public FileSystemLocationSnapshot get() {
                    return snapshotAndCache(file, null);
                }
            });
        }
        return result;
    }

    private FileSystemLocationSnapshot snapshotAndCache(File file, @Nullable SnapshottingFilter filter) {
        InternableString absolutePath = new InternableString(file.getAbsolutePath());
        FileMetadataSnapshot metadata = statAndCache(absolutePath, file);
        return snapshotAndCache(absolutePath, file, metadata, filter);
    }

    private FileMetadataSnapshot statAndCache(InternableString absolutePath, File file) {
        FileMetadataSnapshot metadata = fileSystemMirror.getMetadata(absolutePath.asNonInterned());
        if (metadata == null) {
            metadata = stat.stat(file);
            fileSystemMirror.putMetadata(absolutePath.asInterned(), metadata);
        }
        return metadata;
    }

    private FileSystemLocationSnapshot snapshotAndCache(InternableString absolutePath, File file, FileMetadataSnapshot metadata, @Nullable SnapshottingFilter filter) {
        FileSystemLocationSnapshot fileSystemLocationSnapshot = fileSystemMirror.getSnapshot(absolutePath.asNonInterned());
        if (fileSystemLocationSnapshot == null) {
            AtomicBoolean hasBeenFiltered = new AtomicBoolean(false);
            fileSystemLocationSnapshot = snapshot(absolutePath.asInterned(), filter, file, metadata, hasBeenFiltered);
            if (!hasBeenFiltered.get()) {
                fileSystemMirror.putSnapshot(fileSystemLocationSnapshot);
            }
        }
        return fileSystemLocationSnapshot;
    }

    private FileSystemLocationSnapshot snapshot(String absolutePath, @Nullable SnapshottingFilter filter, File file, FileMetadataSnapshot metadata, AtomicBoolean hasBeenFiltered) {
        String internedName = stringInterner.intern(file.getName());
        switch (metadata.getType()) {
            case Missing:
                return new MissingFileSnapshot(absolutePath, internedName);
            case RegularFile:
                return new RegularFileSnapshot(absolutePath, internedName, hasher.hash(file, metadata.getLength(), metadata.getLastModified()), FileMetadata.from(metadata));
            case Directory:
                SnapshottingFilter.DirectoryWalkerPredicate predicate = filter == null || filter.isEmpty()
                    ? null
                    : filter.getAsDirectoryWalkerPredicate();
                return directorySnapshotter.snapshot(absolutePath, predicate, hasBeenFiltered);
            default:
                throw new IllegalArgumentException("Unrecognized file type: " + metadata.getType());
        }
    }

    @Override
    public FileSystemSnapshot snapshotDirectoryTree(File root, SnapshottingFilter filter) {
        // Could potentially coordinate with a thread that is snapshotting an overlapping directory tree
        String path = root.getAbsolutePath();

        FileSystemLocationSnapshot snapshot = fileSystemMirror.getSnapshot(path);
        if (snapshot != null) {
            return filterSnapshot(snapshot, filter);
        }
        return producingSnapshots.guardByKey(path, new Supplier<FileSystemSnapshot>() {
            @Override
            public FileSystemSnapshot get() {
                FileSystemLocationSnapshot snapshot = fileSystemMirror.getSnapshot(path);
                if (snapshot == null) {
                    snapshot = snapshotAndCache(root, filter);
                    return snapshot.getType() != FileType.Directory ? filterSnapshot(snapshot, filter) : filterSnapshot(snapshot, SnapshottingFilter.EMPTY);
                } else {
                    return filterSnapshot(snapshot, filter);
                }
            }
        });
    }

    @Override
    public FileSystemSnapshotBuilder newFileSystemSnapshotBuilder() {
        return new FileSystemSnapshotBuilder(stringInterner, hasher);
    }

    private FileSystemSnapshot filterSnapshot(FileSystemLocationSnapshot snapshot, SnapshottingFilter filter) {
        if (snapshot.getType() == FileType.Missing) {
            return FileSystemSnapshot.EMPTY;
        }
        if (filter.isEmpty()) {
            return snapshot;
        }
        return FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), snapshot);
    }

    private class InternableString {
        private String string;
        private boolean interned;

        public InternableString(String nonInternedString) {
            this.string = nonInternedString;
        }

        public String asInterned() {
            if (!interned)  {
                interned = true;
                string = stringInterner.intern(string);
            }
            return string;
        }

        public String asNonInterned() {
            return string;
        }
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
