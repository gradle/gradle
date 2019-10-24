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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileSystemNodeWithChildren extends AbstractFileSystemNode {
    private final List<? extends FileSystemNode> children;

    public FileSystemNodeWithChildren(String prefix, List<? extends FileSystemNode> children) {
        super(prefix);
        assert !children.isEmpty();
        this.children = children;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return handleChildren(children, path, 0, new ChildHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleNewChild(int startNextSegment, int insertBefore) {
                return Optional.of(FileSystemNodeWithChildren.this);
            }

            @Override
            public Optional<FileSystemNode> handleChildOfExisting(int startNextSegment, int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return invalidateSingleChild(child, path)
                    .map(invalidatedChild -> withReplacedChild(childIndex, child, invalidatedChild))
                    .map(Optional::of)
                    .orElseGet(() -> {
                        if (children.size() == 1) {
                            return Optional.empty();
                        }
                        List<FileSystemNode> merged = new ArrayList<>(children);
                        merged.remove(childIndex);
                        return Optional.of(new FileSystemNodeWithChildren(getPrefix(), merged));
                    });
            }
        });
    }

    @Override
    public FileSystemNode update(String path, MetadataSnapshot snapshot) {
        return handleChildren(children, path, 0, new ChildHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleNewChild(int startNextSegment, int insertBefore) {
                List<FileSystemNode> newChildren = new ArrayList<>(children);
                newChildren.add(insertBefore, new SnapshotFileSystemNode(path, snapshot));
                return new FileSystemNodeWithChildren(getPrefix(), newChildren);
            }

            @Override
            public FileSystemNode handleChildOfExisting(int startNextSegment, int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return withReplacedChild(childIndex, child, updateSingleChild(child, path, snapshot));
            }
        });
    }

    private FileSystemNode withReplacedChild(int childIndex, FileSystemNode childToReplace, FileSystemNode newChild) {
        if (newChild == childToReplace) {
            return FileSystemNodeWithChildren.this;
        }
        if (children.size() == 1) {
            return new FileSystemNodeWithChildren(getPrefix(), ImmutableList.of(newChild));
        }
        List<FileSystemNode> merged = new ArrayList<>(children);
        merged.set(childIndex, newChild);
        return new FileSystemNodeWithChildren(getPrefix(), merged);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String filePath, int offset) {
        switch (children.size()) {
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return isChildOfOrThis(filePath, offset, onlyChild.getPrefix())
                    ? getChildSnapshot(filePath, offset, onlyChild)
                    : Optional.empty();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (isChildOfOrThis(filePath, offset, firstChild.getPrefix())) {
                    return getChildSnapshot(filePath, offset, firstChild);
                }
                if (isChildOfOrThis(filePath, offset, secondChild.getPrefix())) {
                    return getChildSnapshot(filePath, offset, secondChild);
                }
                return Optional.empty();
            default:
                int foundChild = ListUtils.binarySearch(children, child -> compareToChildOfOrThis(child.getPrefix(), filePath, offset, File.separatorChar));
                return foundChild >= 0
                    ? getChildSnapshot(filePath, offset, children.get(foundChild))
                    : Optional.empty();
        }
    }

    private Optional<MetadataSnapshot> getChildSnapshot(String filePath, int offset, FileSystemNode child) {
        return child.getSnapshot(filePath, offset + child.getPrefix().length() + 1);
    }

    @Override
    public void collect(int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add(getPrefix());
        } else {
            prefixes.add(depth + ":" + getPrefix().replace(File.separatorChar, '/'));
        }
        for (FileSystemNode child : children) {
            child.collect(depth + 1, prefixes);
        }
    }

    @Override
    public FileSystemNode withPrefix(String newPrefix) {
        return new FileSystemNodeWithChildren(newPrefix, children);
    }
}
