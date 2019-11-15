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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractIncompleteSnapshotWithChildren extends AbstractFileSystemNode {
    protected final List<? extends FileSystemNode> children;

    public AbstractIncompleteSnapshotWithChildren(String pathToParent, List<? extends FileSystemNode> children) {
        super(pathToParent);
        this.children = children;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String absolutePath, int offset) {
        return SnapshotUtil.handleChildren(children, absolutePath, offset, new SnapshotUtil.ChildHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleNewChild(int insertBefore) {
                return Optional.of(withIncompleteChildren());
            }

            @Override
            public Optional<FileSystemNode> handleChildOfExisting(int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return SnapshotUtil.invalidateSingleChild(child, absolutePath, offset)
                    .map(invalidatedChild -> withReplacedChild(childIndex, child, invalidatedChild))
                    .map(Optional::of)
                    .orElseGet(() -> {
                        if (children.size() == 1) {
                            return withAllChildrenRemoved();
                        }
                        List<FileSystemNode> merged = new ArrayList<>(children);
                        merged.remove(childIndex);
                        return Optional.of(withIncompleteChildren(getPathToParent(), merged));
                    });
            }
        });
    }

    @Override
    public FileSystemNode store(String absolutePath, int offset, MetadataSnapshot snapshot) {
        return SnapshotUtil.handleChildren(children, absolutePath, offset, new SnapshotUtil.ChildHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleNewChild(int insertBefore) {
                List<FileSystemNode> newChildren = new ArrayList<>(children);
                newChildren.add(insertBefore, snapshot.asFileSystemNode(absolutePath.substring(offset)));
                return withIncompleteChildren(getPathToParent(), newChildren);
            }

            @Override
            public FileSystemNode handleChildOfExisting(int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return withReplacedChild(childIndex, child, SnapshotUtil.storeSingleChild(child, absolutePath, offset, snapshot));
            }
        });
    }

    @Override
    public FileSystemNode withPathToParent(String newPathToParent) {
        return getPathToParent().equals(newPathToParent)
            ? this
            : withIncompleteChildren(newPathToParent, children);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String absolutePath, int offset) {
        return SnapshotUtil.getMetadataFromChildren(children, absolutePath, offset, Optional::empty);
    }

    /**
     * Returns an updated node with the same children. The list of children are
     * incomplete, even if they were complete before.
     */
    protected abstract FileSystemNode withIncompleteChildren();

    /**
     * Returns an updated node with an updated list of children.
     *
     * Caller must ensure the child list is not be mutated as the method
     * doesn't make a defensive copy.
     */
    protected abstract FileSystemNode withIncompleteChildren(String prefix, List<? extends FileSystemNode> newChildren);

    /**
     * Returns an updated node with all children removed, or {@link Optional#empty()}
     * if the node without children would contain no useful information to keep around.
     */
    protected abstract Optional<FileSystemNode> withAllChildrenRemoved();

    private FileSystemNode withReplacedChild(int childIndex, FileSystemNode childToReplace, FileSystemNode newChild) {
        if (newChild == childToReplace) {
            return AbstractIncompleteSnapshotWithChildren.this;
        }
        if (children.size() == 1) {
            return withIncompleteChildren(getPathToParent(), ImmutableList.of(newChild));
        }
        List<FileSystemNode> merged = new ArrayList<>(children);
        merged.set(childIndex, newChild);
        return withIncompleteChildren(getPathToParent(), merged);
    }
}
