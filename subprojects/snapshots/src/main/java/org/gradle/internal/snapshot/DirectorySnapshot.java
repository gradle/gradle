/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A file snapshot which can have children (i.e. a directory).
 */
public class DirectorySnapshot extends AbstractFileSystemLocationSnapshot implements FileSystemLocationSnapshot {
    private final List<FileSystemLocationSnapshot> children;
    private final HashCode contentHash;

    public DirectorySnapshot(String absolutePath, String name, List<FileSystemLocationSnapshot> children, HashCode contentHash) {
        super(absolutePath, name);
        this.children = children;
        this.contentHash = contentHash;
    }

    @Override
    public HashCode getHash() {
        return contentHash;
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        return other instanceof DirectorySnapshot;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        if (!visitor.preVisitDirectory(this)) {
            return;
        }
        for (FileSystemLocationSnapshot child : children) {
            child.accept(visitor);
        }
        visitor.postVisitDirectory(this);
    }

    public List<FileSystemLocationSnapshot> getChildren() {
        return children;
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String filePath, int offset) {
        return FileSystemNode.thisOrGet(
            this, filePath, offset,
            () -> {
                for (FileSystemLocationSnapshot child : getChildren()) {
                    if (AbstractFileSystemNode.isChildOfOrThis(filePath, offset, child.getName())) {
                        int endOfThisSegment = child.getName().length() + offset;
                        return child.getSnapshot(filePath, endOfThisSegment + 1);
                    }
                }
                return Optional.of(missingSnapshotForAbsolutePath(filePath));
            });
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return AbstractFileSystemNode.handleChildren(children, path, 0, new AbstractFileSystemNode.ChildHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleNewChild(int startNextSegment, int insertBefore) {
                return children.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new FileSystemNodeWithChildren(getPrefix(), children));
            }

            @Override
            public Optional<FileSystemNode> handleChildOfExisting(int startNextSegment, int childIndex) {
                FileSystemLocationSnapshot foundChild = children.get(childIndex);
                int indexForSubSegment = foundChild.getPrefix().length();
                Optional<FileSystemNode> invalidated = indexForSubSegment == path.length()
                    ? Optional.empty()
                    : foundChild.invalidate(path.substring(indexForSubSegment + 1));
                if (children.size() == 1) {
                    return invalidated.map(it -> new FileSystemNodeWithChildren(getPrefix(), ImmutableList.of(it)));
                }

                return invalidated
                    .map(invalidatedChild -> {
                        ArrayList<FileSystemNode> newChildren = new ArrayList<>(children);
                        newChildren.set(childIndex, invalidatedChild);
                        return Optional.<FileSystemNode>of(new FileSystemNodeWithChildren(getPrefix(), newChildren));
                    }).orElseGet(() -> {
                        if (children.size() == 2) {
                            FileSystemLocationSnapshot singleChild = childIndex == 0 ? children.get(1) : children.get(0);
                            return Optional.of(new FileSystemNodeWithChildren(getPrefix(), ImmutableList.of(singleChild)));
                        }
                        ArrayList<FileSystemLocationSnapshot> newChildren = new ArrayList<>(children);
                        newChildren.remove(childIndex);
                        return Optional.of(new FileSystemNodeWithChildren(getPrefix(), newChildren));
                    });
            }
        });
    }
}
