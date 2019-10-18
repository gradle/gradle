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
import java.util.stream.Collectors;

class NodeWithChildren extends AbstractNode {
    private final List<Node> children;

    public NodeWithChildren(String prefix, List<Node> children) {
        super(prefix);
        assert !children.isEmpty();
        ArrayList<Node> copy = new ArrayList<>(children);
        copy.sort(Comparator.comparing(Node::getPrefix, pathComparator()));
        if (!copy.equals(children)) {
            throw new RuntimeException("Arrrrgghhh, this is not sorted: " + children.stream().map(Node::getPrefix).collect(Collectors.joining(", ")));
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
                        List<Node> merged = new ArrayList<>(children);
                        invalidatedChild.ifPresent(newChild -> merged.set(childIndex, newChild));
                        if (!invalidatedChild.isPresent()) {
                            merged.remove(childIndex);
                        }
                        return Optional.of(new NodeWithChildren(getPrefix(), merged));
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
    public Node update(String path, FileSystemLocationSnapshot snapshot) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<Node>() {
            @Override
            public Node handleDescendant() {
                return handleChildren(getPrefix(), path, new ChildHandler<Node>() {
                    @Override
                    public Node handleNewChild(int startNextSegment, int insertBefore) {
                        List<Node> newChildren = new ArrayList<>(children);
                        newChildren.add(insertBefore, new SnapshotNode(path.substring(startNextSegment), snapshot));
                        return new NodeWithChildren(getPrefix(), newChildren);
                    }

                    @Override
                    public Node handleChildOfExisting(int startNextSegment, int childIndex) {
                        Node child = children.get(childIndex);
                        Node newChild = child.update(path.substring(startNextSegment), snapshot);
                        if (children.size() == 1) {
                            return new NodeWithChildren(getPrefix(), ImmutableList.of(newChild));
                        }
                        List<Node> merged = new ArrayList<>(children);
                        merged.set(childIndex, newChild);
                        return new NodeWithChildren(getPrefix(), merged);
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
                ImmutableList<Node> newChildren = pathComparator().compare(newThis.getPrefix(), sibling.getPrefix()) < 0
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
