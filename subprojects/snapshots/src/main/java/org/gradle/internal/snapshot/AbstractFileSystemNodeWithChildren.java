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

public abstract class AbstractFileSystemNodeWithChildren extends AbstractFileSystemNode {
    protected final List<? extends FileSystemNode> children;

    public AbstractFileSystemNodeWithChildren(String prefix, List<? extends FileSystemNode> children) {
        super(prefix);
        this.children = children;
    }

    protected abstract Optional<MetadataSnapshot> getThisSnapshot();

    protected abstract FileSystemNode createCopy(String prefix, List<? extends FileSystemNode> newChildren);
    protected abstract Optional<FileSystemNode> withNoChildren();
    protected abstract FileSystemNode withUnkownChildInvalidated();

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return handleChildren(children, path, new ChildHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleNewChild(int insertBefore) {
                return Optional.of(withUnkownChildInvalidated());
            }

            @Override
            public Optional<FileSystemNode> handleChildOfExisting(int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return invalidateSingleChild(child, path)
                    .map(invalidatedChild -> withReplacedChild(childIndex, child, invalidatedChild))
                    .map(Optional::of)
                    .orElseGet(() -> {
                        if (children.size() == 1) {
                            return withNoChildren();
                        }
                        List<FileSystemNode> merged = new ArrayList<>(children);
                        merged.remove(childIndex);
                        return Optional.of(createCopy(getPrefix(), merged));
                    });
            }
        });
    }

    @Override
    public FileSystemNode update(String path, MetadataSnapshot snapshot) {
        return handleChildren(children, path, new ChildHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleNewChild(int insertBefore) {
                List<FileSystemNode> newChildren = new ArrayList<>(children);
                newChildren.add(insertBefore, new SnapshotFileSystemNode(path, snapshot));
                return createCopy(getPrefix(), newChildren);
            }

            @Override
            public FileSystemNode handleChildOfExisting(int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return withReplacedChild(childIndex, child, updateSingleChild(child, path, snapshot));
            }
        });
    }

    private FileSystemNode withReplacedChild(int childIndex, FileSystemNode childToReplace, FileSystemNode newChild) {
        if (newChild == childToReplace) {
            return AbstractFileSystemNodeWithChildren.this;
        }
        if (children.size() == 1) {
            return createCopy(getPrefix(), ImmutableList.of(newChild));
        }
        List<FileSystemNode> merged = new ArrayList<>(children);
        merged.set(childIndex, newChild);
        return createCopy(getPrefix(), merged);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String filePath, int offset) {
        return getThisSnapshot()
            .map(current -> FileSystemNode.thisOrGet(
                current, filePath, offset,
                () -> getSnapshotFromChildren(filePath, offset)
            ))
            .orElseGet(() -> getSnapshotFromChildren(filePath, offset));
    }

    private Optional<MetadataSnapshot> getSnapshotFromChildren(String filePath, int offset) {
        switch (children.size()) {
            case 0:
                return Optional.empty();
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return isChildOfOrThis(filePath, offset, onlyChild.getPrefix())
                    ? getSnapshotFromChild(filePath, offset, onlyChild)
                    : Optional.empty();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (isChildOfOrThis(filePath, offset, firstChild.getPrefix())) {
                    return getSnapshotFromChild(filePath, offset, firstChild);
                }
                if (isChildOfOrThis(filePath, offset, secondChild.getPrefix())) {
                    return getSnapshotFromChild(filePath, offset, secondChild);
                }
                return Optional.empty();
            default:
                int foundChild = ListUtils.binarySearch(children, child -> compareToChildOfOrThis(child.getPrefix(), filePath, offset, File.separatorChar));
                return foundChild >= 0
                    ? getSnapshotFromChild(filePath, offset, children.get(foundChild))
                    : Optional.empty();
        }
    }

    private Optional<MetadataSnapshot> getSnapshotFromChild(String filePath, int offset, FileSystemNode child) {
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
        return createCopy(newPrefix, children);
    }
}
