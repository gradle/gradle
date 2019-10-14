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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileMetadata;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.snapshot.impl.FileSystemSnapshotFilter;
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultVirtualFileSystem implements VirtualFileSystem, Closeable {
    // On Windows, / and \ are separators, on Unix only / is a separator.
    private static final Splitter FILE_PATH_SPLITTER = File.separatorChar != '/'
        ? Splitter.on(CharMatcher.anyOf("/" + File.separator))
        : Splitter.on('/');
    private final RootNode root = new RootNode();
    private final Stat stat;
    private final DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;
    private final ExecutorService executorService;

    public DefaultVirtualFileSystem(FileHasher hasher, Interner<String> stringInterner, Stat stat, ExecutorService executorService, String... defaultExcludes) {
        this.stat = stat;
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, defaultExcludes);
        this.hasher = hasher;
        this.executorService = executorService;
    }

    @Override
    public <T> T read(String location, Function<FileSystemLocationSnapshot, T> visitor) {
        return visitor.apply(readLocation(location).getSnapshot());
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        List<String> pathSegments = getPathSegments(location);
        String name = pathSegments.get(pathSegments.size() - 1);
        Node foundParent = findParentNotCreating(pathSegments);
        Node existingChild = foundParent != null ? foundParent.getChild(name) : null;
        if (existingChild != null && existingChild.getType() != Node.Type.UNKNOWN) {
            return mapRegularFileContentHash(visitor, existingChild);
        }
        File file = new File(location);
        FileMetadataSnapshot stat = this.stat.stat(file);
        if (stat.getType() != FileType.RegularFile) {
            return Optional.empty();
        }
        HashCode hashCode = hasher.hash(file, stat.getLength(), stat.getLastModified());
        RegularFileSnapshot snapshot = new RegularFileSnapshot(location, file.getName(), hashCode, FileMetadata.from(stat));
        return Optional.ofNullable(visitor.apply(mutateVirtualFileSystem(() -> {
            Node parent = findOrCreateParent(pathSegments);
            Node node = parent.replaceChild(snapshot.getName(), it -> new FileNode(it, snapshot), existing -> existing.getType() == Node.Type.UNKNOWN);
            return node.getSnapshot().getHash();
        })));
    }

    private <T> Optional<T> mapRegularFileContentHash(Function<HashCode, T> visitor, Node existingChild) {
        FileSystemLocationSnapshot snapshot = existingChild.getSnapshot();
        return snapshot.getType() == FileType.RegularFile
            ? Optional.ofNullable(visitor.apply(snapshot.getHash()))
            : Optional.empty();
    }

    @Override
    public void read(String location, SnapshottingFilter filter, Consumer<FileSystemLocationSnapshot> visitor) {
        if (filter.isEmpty()) {
            visitor.accept(readLocation(location).getSnapshot());
        } else {
            FileSystemLocationSnapshot unfilteredSnapshot = readLocation(location).getSnapshot();
            FileSystemSnapshot filteredSnapshot = FileSystemSnapshotFilter.filterSnapshot(filter.getAsSnapshotPredicate(), unfilteredSnapshot);
            if (filteredSnapshot instanceof FileSystemLocationSnapshot) {
                visitor.accept((FileSystemLocationSnapshot) filteredSnapshot);
            }
        }
    }

    private Node createNode(FileSystemLocationSnapshot snapshot, Node parent) {
        switch (snapshot.getType()) {
            case RegularFile:
                return new FileNode(parent, (RegularFileSnapshot) snapshot);
            case Missing:
                return new MissingFileNode(parent, snapshot.getAbsolutePath(), snapshot.getName());
            case Directory:
                return new CompleteDirectoryNode(parent, (DirectorySnapshot) snapshot);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private FileSystemLocationSnapshot snapshot(String location) {
        File file = new File(location);
        FileMetadataSnapshot stat = this.stat.stat(file);
        switch (stat.getType()) {
            case RegularFile:
                HashCode hash = hasher.hash(file, stat.getLength(), stat.getLastModified());
                return new RegularFileSnapshot(location, file.getName(), hash, FileMetadata.from(stat));
            case Missing:
                return new MissingFileSnapshot(location, file.getName());
            case Directory:
                return directorySnapshotter.snapshot(location, null, new AtomicBoolean(false));
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Node readLocation(String location) {
        List<String> pathSegments = getPathSegments(location);
        Node foundParent = findParentNotCreating(pathSegments);
        String name = pathSegments.get(pathSegments.size() - 1);
        Node existingChild = foundParent != null ? foundParent.getChild(name) : null;
        if (existingChild != null && existingChild.getType() != Node.Type.UNKNOWN) {
            return existingChild;
        }

        FileSystemLocationSnapshot snapshot = snapshot(location);
        return mutateVirtualFileSystem(() -> {
            Node parent = findOrCreateParent(pathSegments);
            return parent.replaceChild(
                name,
                it -> createNode(snapshot, it),
                current -> current.getType() == Node.Type.UNKNOWN);
        });
    }

    private void mutateVirtualFileSystem(Runnable action) {
        mutateVirtualFileSystem(() -> {
            action.run();
            return null;
        });
    }

    private <T> T mutateVirtualFileSystem(Callable<T> action) {
        try {
            return executorService.submit(action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause();
        }
    }

    private Node findOrCreateParent(List<String> pathSegments) {
        Node foundNode = root;
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            String pathSegment = pathSegments.get(i);
            foundNode = foundNode.getOrCreateChild(pathSegment, parent -> new DefaultNode(pathSegment, parent));
        }
        return foundNode;
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        locations.forEach(location -> {
            List<String> pathSegments = getPathSegments(location);
            Node foundParent = findParentNotCreating(pathSegments);
            String name = pathSegments.get(pathSegments.size() - 1);
            if (foundParent != null && foundParent.getChild(name) != null) {
                mutateVirtualFileSystem(() -> {
                    Node parent = findParentNotCreating(pathSegments);
                    if (parent != null) {
                        parent.removeChild(name);
                    }
                });
            }
        });
        action.run();
    }

    @Override
    public void invalidateAll() {
        if (executorService.isShutdown()) {
            return;
        }
        mutateVirtualFileSystem(root::clear);
    }

    @Override
    public void updateWithKnownSnapshot(String location, FileSystemLocationSnapshot snapshot) {
        List<String> pathSegments = getPathSegments(location);
        mutateVirtualFileSystem(() -> {
            Node parent = findOrCreateParent(pathSegments);
            parent.replaceChild(
                pathSegments.get(pathSegments.size() - 1),
                it -> CompleteDirectoryNode.convertToNode(snapshot, parent),
                old -> true);
        });
    }

    @Nullable
    private Node findParentNotCreating(List<String> pathSegments) {
        Node foundNode = root;
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            String pathSegment = pathSegments.get(i);
            foundNode = foundNode.getChild(pathSegment);
            if (foundNode == null) {
                return null;
            }
        }
        return foundNode;
    }

    private static List<String> getPathSegments(String path) {
        return FILE_PATH_SPLITTER.splitToList(path);
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
    }
}
