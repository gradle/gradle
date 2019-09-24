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

import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileMetadata;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.impl.DirectorySnapshotter;
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class DefaultVirtualFileSystem implements VirtualFileSystem {
    // On Windows, / and \ are separators, on Unix only / is a separator.
    private static final String FILE_PATH_SEPARATORS = File.separatorChar != '/' ? ("/" + File.separator) : File.separator;
    private final Node root = new RootNode();
    private final Stat stat;
    private final DirectorySnapshotter directorySnapshotter;
    private final FileHasher hasher;

    public DefaultVirtualFileSystem(Stat stat, DirectorySnapshotter directorySnapshotter, FileHasher hasher) {
        this.stat = stat;
        this.directorySnapshotter = directorySnapshotter;
        this.hasher = hasher;
    }

    @Override
    public void read(String location, FileSystemSnapshotVisitor visitor) {
        findLocation(location, parent -> createNode(location, parent))
            .accept(visitor);
    }

    protected Node createNode(String location, Node parent) {
        File file = new File(location);
        FileMetadataSnapshot stat = this.stat.stat(file);
        switch (stat.getType()) {
            case RegularFile:
                HashCode hash = hasher.hash(file);
                return new FileNode(new RegularFileSnapshot(location, file.getName(), hash, FileMetadata.from(stat)));
            case Missing:
                return new MissingFileNode(location, file.getName());
            case Directory:
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) directorySnapshotter.snapshot(location, null, new AtomicBoolean(false));
                return new CompleteDirectoryNode(parent, directorySnapshot);
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Node findLocation(String location, Function<Node, Node> nodeCreator) {
        String[] pathSegments = getPathSegments(location);
        Node parent = findParent(pathSegments);
        return parent.replaceChild(
            pathSegments[pathSegments.length - 1],
            nodeCreator,
            current -> current.getType() != Node.Type.UNKNOWN
                ? current
                : nodeCreator.apply(parent)
        );
    }

    private Node findParent(String[] pathSegments) {
        Node foundNode = root;
        for (int i = 0; i < pathSegments.length - 1; i++) {
            String pathSegment = pathSegments[i];
            foundNode = foundNode.getOrCreateChild(pathSegment, parent -> new DefaultNode(pathSegment, parent));
        }
        return foundNode;
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        locations.forEach(location -> {
            String[] pathSegments = getPathSegments(location);
            Node parentLocation = findParentNotCreating(pathSegments);
            if (parentLocation != null) {
                String name = pathSegments[pathSegments.length - 1];
                parentLocation.replaceChild(name, parent -> null, nodeToBeReplaced -> null);
            }
        });
        action.run();
    }

    @Nullable
    private Node findParentNotCreating(String[] pathSegments) {
        Node foundNode = root;
        for (int i = 0; i < pathSegments.length - 1; i++) {
            String pathSegment = pathSegments[i];
            foundNode = foundNode.getOrCreateChild(pathSegment, parent -> null);
            if (foundNode == null) {
                return null;
            }
        }
        return foundNode;
    }

    public static String[] getPathSegments(String path) {
        return path.split(FILE_PATH_SEPARATORS);
    }
}
