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
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.snapshot.impl.FileSystemSnapshotFilter;
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultVirtualFileSystem implements VirtualFileSystem {
    // On Windows, / and \ are separators, on Unix only / is a separator.
    private static final Splitter FILE_PATH_SPLITTER = File.separatorChar != '/'
        ? Splitter.on(CharMatcher.anyOf("/" + File.separator))
        : Splitter.on('/');
    private final RootNode root = new RootNode();
    private final Stat stat;
    private final DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;
    private final Set<List<String>> currentlyModifiedLocations = new HashSet<>();

    public DefaultVirtualFileSystem(FileHasher hasher, Interner<String> stringInterner, Stat stat, String... defaultExcludes) {
        this.stat = stat;
        this.directorySnapshotter = new DirectorySnapshotter(hasher, stringInterner, defaultExcludes);
        this.hasher = hasher;
    }

    @Override
    public <T> T read(String location, Function<FileSystemLocationSnapshot, T> visitor) {
        return visitor.apply(readLocation(location).getSnapshot());
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        FileSystemLocationSnapshot snapshot = readLocation(location).getSnapshot();
        if (snapshot.getType() == FileType.RegularFile) {
            return Optional.ofNullable(visitor.apply(snapshot.getHash()));
        }
        return Optional.empty();
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

    protected Node createNode(String location, Node parent) {
        File file = new File(location);
        FileMetadataSnapshot stat = this.stat.stat(file);
        switch (stat.getType()) {
            case RegularFile:
                HashCode hash = hasher.hash(file);
                return new FileNode(parent, new RegularFileSnapshot(location, file.getName(), hash, FileMetadata.from(stat)));
            case Missing:
                return new MissingFileNode(parent, location, file.getName());
            case Directory:
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) directorySnapshotter.snapshot(location, null, new AtomicBoolean(false));
                return new CompleteDirectoryNode(parent, directorySnapshot);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Node readLocation(String location) {
        List<String> pathSegments = getPathSegments(location);
        Function<Node, Node> nodeCreator = parent -> createNode(location, parent);
        Node foundParent = findParent(pathSegments);
        String name = pathSegments.get(pathSegments.size() - 1);
        Node existingChild = foundParent.getChild(name);
        if (existingChild != null && existingChild.getType() != Node.Type.UNKNOWN) {
            return existingChild;
        }

        return modifyLocation(pathSegments,
            parent -> parent.replaceChild(
                name,
                nodeCreator,
                current -> current.getType() == Node.Type.UNKNOWN));
    }

    private <T> T modifyLocation(List<String> pathSegments, Function<Node, T> modifyParent) {
        try {
            Node foundNode = null;
            List<String> pathToMutableParent = null;
            synchronized (currentlyModifiedLocations) {
                boolean canModifyLocation = false;
                while (!canModifyLocation) {
                    foundNode = root;
                    int mutableParentIndex = 0;
                    for (int i = 0; i < pathSegments.size() - 1; i++) {
                        String pathSegment = pathSegments.get(i);
                        foundNode = foundNode.getOrCreateChild(pathSegment, parent -> new DefaultNode(pathSegment, parent));
                        if (foundNode instanceof AbstractNodeWithMutableChildren) {
                            mutableParentIndex = i;
                        }
                    }
                    pathToMutableParent = pathSegments.subList(0, mutableParentIndex + 1);
                    canModifyLocation = canModifyLocation(pathToMutableParent);
                    if (!canModifyLocation) {
                        currentlyModifiedLocations.wait();
                    }
                }
                currentlyModifiedLocations.add(pathToMutableParent);
            }
            T result = modifyParent.apply(foundNode);
            synchronized (currentlyModifiedLocations) {
                currentlyModifiedLocations.remove(pathToMutableParent);
                currentlyModifiedLocations.notifyAll();
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private boolean canModifyLocation(List<String> locationToModify) {
        return currentlyModifiedLocations.stream()
            .noneMatch(modifiedLocation -> isPrefixOrSuffix(modifiedLocation, locationToModify));
    }

    private static boolean isPrefixOrSuffix(List<String> list1, List<String> list2) {
        int size1 = list1.size();
        int size2 = list2.size();
        if (size1 == size2) {
            return list1.equals(list2);
        }
        int prefixSize = Math.min(size1, size2);
        for (int i = 0; i < prefixSize; i++) {
            if (!list1.get(i).equals(list2.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Node findParent(List<String> pathSegments) {
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
            Node parentLocation = findParentNotCreating(pathSegments);
            if (parentLocation != null) {
                modifyLocation(pathSegments,
                    parent -> {
                        String name = pathSegments.get(pathSegments.size() - 1);
                        parent.removeChild(name);
                        return null;
                    }
                );
            }
        });
        action.run();
    }

    @Override
    public synchronized void invalidateAll() {
        root.clear();
    }

    @Override
    public void updateWithKnownSnapshot(String location, FileSystemLocationSnapshot snapshot) {
        List<String> pathSegments = getPathSegments(location);
        Node parent = findParent(pathSegments);
        parent.replaceChild(
            pathSegments.get(pathSegments.size() - 1),
            it -> CompleteDirectoryNode.convertToNode(snapshot, parent),
            old -> true);
    }

    @Nullable
    private Node findParentNotCreating(List<String> pathSegments) {
        Node foundNode = root;
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            String pathSegment = pathSegments.get(i);
            foundNode = foundNode.getOrCreateChild(pathSegment, parent -> null);
            if (foundNode == null) {
                return null;
            }
        }
        return foundNode;
    }

    private static List<String> getPathSegments(String path) {
        return FILE_PATH_SPLITTER.splitToList(path);
    }
}
