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
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

class NodeWithChildren extends AbstractNode {
    private final List<Node> children;

    public NodeWithChildren(String prefix, List<Node> children) {
        super(prefix);
        assert !children.isEmpty();
        ArrayList<Node> copy = new ArrayList<>(children);
        copy.sort(Comparator.comparing(Node::getPrefix));
        if (!copy.equals(children)) {
            throw new RuntimeException("Arrrrgghhh");
        }
        this.children = children;
    }

    @Override
    public Optional<Node> invalidate(String path) {
        return handlePrefix(getPrefix(), path, new InvalidateHandler() {
            @Override
            public Optional<Node> handleDescendant() {
                return handleChildren(getPrefix(), path, new ChildHandler<Optional<Node>>() {
                    @Override
                    public Optional<Node> handleNewChild(int startNextSegment, int insertBefore) {
                        return Optional.of(NodeWithChildren.this);
                    }

                    @Override
                    public Optional<Node> handleChildOfExisting(int startNextSegment, int childIndex) {
                        Node child = children.get(childIndex);
                        Optional<Node> invalidatedChild = child.invalidate(path.substring(startNextSegment));
                        if (children.size() == 1) {
                            return invalidatedChild.map(it -> new NodeWithChildren(getPrefix(), ImmutableList.of(it)));
                        }
                        ImmutableList.Builder<Node> merged = ImmutableList.builderWithExpectedSize(
                            invalidatedChild.isPresent()
                                ? children.size()
                                : children.size() - 1
                        );
                        if (childIndex > 0) {
                            merged.addAll(children.subList(0, childIndex));
                        }
                        invalidatedChild.ifPresent(merged::add);
                        if (childIndex + 1 < children.size()) {
                            merged.addAll(children.subList(childIndex + 1, children.size()));
                        }
                        return Optional.of(new NodeWithChildren(getPrefix(), merged.build()));
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

        int childIndex = binarySearchChildren(path, startNextSegment);
        if (childIndex >= 0) {
            return childHandler.handleChildOfExisting(startNextSegment, childIndex);
        }
        return childHandler.handleNewChild(startNextSegment, -childIndex - 1);
    }

    private int binarySearchChildren(String path, int startNextSegment) {
        int low = 0;
        int high = children.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Node midVal = children.get(mid);
            int cmp = sizeOfCommonPrefix(midVal.getPrefix(), path, startNextSegment, File.separatorChar, (commonPrefix, childSmaller) -> {
                if (commonPrefix > 0) {
                    return 0;
                }
                return childSmaller ? -1 : 1;
            });

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }

    @Override
    public Node update(String path, FileSystemLocationSnapshot snapshot) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<Node>() {
            @Override
            public Node handleDescendant() {
                return handleChildren(getPrefix(), path, new ChildHandler<Node>() {
                    @Override
                    public Node handleNewChild(int startNextSegment, int insertBefore) {
                        ImmutableList.Builder<Node> newChildren = ImmutableList.builder();
                        if (insertBefore > 0) {
                            newChildren.addAll(children.subList(0, insertBefore));
                        }
                        newChildren.add(new SnapshotNode(path.substring(startNextSegment), snapshot));
                        if (insertBefore < children.size()) {
                            newChildren.addAll(children.subList(insertBefore, children.size()));
                        }

                        return new NodeWithChildren(getPrefix(), newChildren.build());
                    }

                    @Override
                    public Node handleChildOfExisting(int startNextSegment, int childIndex) {
                        ImmutableList.Builder<Node> newChildren = ImmutableList.builder();
                        if (childIndex > 0) {
                            newChildren.addAll(children.subList(0, childIndex));
                        }
                        newChildren.add(children.get(childIndex).update(path.substring(startNextSegment), snapshot));
                        if (childIndex + 1 < children.size()) {
                            newChildren.addAll(children.subList(childIndex + 1, children.size()));
                        }

                        return new NodeWithChildren(getPrefix(), newChildren.build());
                    }
                });
            }

            @Override
            public Node handleParent() {
                return new SnapshotNode(path, snapshot);
            }

            @Override
            public Node handleSame() {
                return new SnapshotNode(path, snapshot);
            }

            @Override
            public Node handleDifferent(int commonPrefixLength) {
                String commonPrefix = getPrefix().substring(0, commonPrefixLength);
                Node newThis = new NodeWithChildren(getPrefix().substring(commonPrefixLength + 1), children);
                Node sibling = new SnapshotNode(path.substring(commonPrefixLength + 1), snapshot);
                ImmutableList<Node> newChildren = newThis.getPrefix().compareTo(sibling.getPrefix()) < 0
                    ? ImmutableList.of(newThis, sibling)
                    : ImmutableList.of(sibling, newThis);
                return new NodeWithChildren(commonPrefix, newChildren);
            }
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        if (!isChildOfOrThis(filePath, offset, getPrefix())) {
            return Optional.empty();
        }
        int startNextSegment = offset + getPrefix().length() + 1;
        for (Node child : children) {
            Optional<FileSystemLocationSnapshot> childSnapshot = child.getSnapshot(filePath, startNextSegment);
            if (childSnapshot.isPresent()) {
                return childSnapshot;
            }
        }
        return Optional.empty();
    }

    @Override
    public void collect(int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add(getPrefix());
        } else {
            prefixes.add(depth + ":" + getPrefix().replace(File.separatorChar, '/'));
        }
        for (Node child : children) {
            child.collect(depth + 1, prefixes);
        }
    }
}
