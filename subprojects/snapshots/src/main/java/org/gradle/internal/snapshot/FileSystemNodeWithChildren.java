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
import org.gradle.internal.vfs.impl.AbstractFileSystemNode;
import org.gradle.internal.vfs.impl.ListUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileSystemNodeWithChildren extends AbstractFileSystemNode {
    private final List<FileSystemNode> children;

    public FileSystemNodeWithChildren(String prefix, List<FileSystemNode> children) {
        super(prefix);
        assert !children.isEmpty();
        this.children = children;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return handlePrefix(getPrefix(), path, new InvalidateHandler() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return handleChildren(getPrefix(), path, new ChildHandler<Optional<FileSystemNode>>() {
                    @Override
                    public Optional<FileSystemNode> handleNewChild(int startNextSegment, int insertBefore) {
                        return Optional.of(FileSystemNodeWithChildren.this);
                    }

                    @Override
                    public Optional<FileSystemNode> handleChildOfExisting(int startNextSegment, int childIndex) {
                        FileSystemNode child = children.get(childIndex);
                        Optional<FileSystemNode> invalidatedChild = child.invalidate(path.substring(startNextSegment));
                        if (children.size() == 1) {
                            return invalidatedChild.map(it -> new FileSystemNodeWithChildren(getPrefix(), ImmutableList.of(it)));
                        }
                        List<FileSystemNode> merged = new ArrayList<>(children);
                        invalidatedChild.ifPresent(newChild -> merged.set(childIndex, newChild));
                        if (!invalidatedChild.isPresent()) {
                            merged.remove(childIndex);
                        }
                        return Optional.of(new FileSystemNodeWithChildren(getPrefix(), merged));
                    }
                });
            }
        });
    }

    interface ChildHandler<T> {
        T handleNewChild(int startNextSegment, int insertBefore);
        T handleChildOfExisting(int startNextSegment, int childIndex);
    }

    private <T> T handleChildren(String prefix, String path, ChildHandler<T> childHandler) {
        int startNextSegment = prefix.length() + 1;

        int childIndex = ListUtils.binarySearch(
            children,
            candidate -> compareWithCommonPrefix(candidate.getPrefix(), path, startNextSegment, File.separatorChar)
        );
        if (childIndex >= 0) {
            return childHandler.handleChildOfExisting(startNextSegment, childIndex);
        }
        return childHandler.handleNewChild(startNextSegment, -childIndex - 1);
    }

    @Override
    public FileSystemNode update(String path, FileSystemLocationSnapshot snapshot) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return handleChildren(getPrefix(), path, new ChildHandler<FileSystemNode>() {
                    @Override
                    public FileSystemNode handleNewChild(int startNextSegment, int insertBefore) {
                        List<FileSystemNode> newChildren = new ArrayList<>(children);
                        newChildren.add(insertBefore, new SnapshotFileSystemNode(path.substring(startNextSegment), snapshot));
                        return new FileSystemNodeWithChildren(getPrefix(), newChildren);
                    }

                    @Override
                    public FileSystemNode handleChildOfExisting(int startNextSegment, int childIndex) {
                        FileSystemNode child = children.get(childIndex);
                        FileSystemNode newChild = child.update(path.substring(startNextSegment), snapshot);
                        if (children.size() == 1) {
                            return new FileSystemNodeWithChildren(getPrefix(), ImmutableList.of(newChild));
                        }
                        List<FileSystemNode> merged = new ArrayList<>(children);
                        merged.set(childIndex, newChild);
                        return new FileSystemNodeWithChildren(getPrefix(), merged);
                    }
                });
            }

            @Override
            public FileSystemNode handleParent() {
                return new SnapshotFileSystemNode(path, snapshot);
            }

            @Override
            public FileSystemNode handleSame() {
                return new SnapshotFileSystemNode(path, snapshot);
            }

            @Override
            public FileSystemNode handleDifferent(int commonPrefixLength) {
                String commonPrefix = getPrefix().substring(0, commonPrefixLength);
                FileSystemNode newThis = new FileSystemNodeWithChildren(getPrefix().substring(commonPrefixLength + 1), children);
                FileSystemNode sibling = new SnapshotFileSystemNode(path.substring(commonPrefixLength + 1), snapshot);
                ImmutableList<FileSystemNode> newChildren = pathComparator().compare(newThis.getPrefix(), sibling.getPrefix()) < 0
                    ? ImmutableList.of(newThis, sibling)
                    : ImmutableList.of(sibling, newThis);
                return new FileSystemNodeWithChildren(commonPrefix, newChildren);
            }
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        int startNextSegment = offset + getPrefix().length() + 1;
        switch (children.size()) {
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return isChildOfOrThis(filePath, startNextSegment, onlyChild.getPrefix())
                    ? onlyChild.getSnapshot(filePath, startNextSegment)
                    : Optional.empty();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (isChildOfOrThis(filePath, startNextSegment, firstChild.getPrefix())) {
                    return firstChild.getSnapshot(filePath, startNextSegment);
                }
                if (isChildOfOrThis(filePath, startNextSegment, secondChild.getPrefix())) {
                    return secondChild.getSnapshot(filePath, startNextSegment);
                }
                return Optional.empty();
            default:
                int foundChild = ListUtils.binarySearch(children, child -> compareToChildOfOrThis(child.getPrefix(), filePath, startNextSegment, File.separatorChar));
                return foundChild >= 0
                    ? children.get(foundChild).getSnapshot(filePath, startNextSegment)
                    : Optional.empty();
        }
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
}
