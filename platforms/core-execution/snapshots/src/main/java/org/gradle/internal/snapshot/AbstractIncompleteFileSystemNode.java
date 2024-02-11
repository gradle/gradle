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

import org.gradle.internal.file.FileType;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class AbstractIncompleteFileSystemNode implements FileSystemNode {
    protected final ChildMap<FileSystemNode> children;

    @SuppressWarnings("unchecked")
    public AbstractIncompleteFileSystemNode(ChildMap<? extends FileSystemNode> children) {
        this.children = (ChildMap<FileSystemNode>) children;
    }

    @Override
    public Optional<FileSystemNode> getNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return SnapshotUtil.getChild(children, targetPath, caseSensitivity);
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.invalidate(targetPath, caseSensitivity, new ChildMap.InvalidationHandler<FileSystemNode, FileSystemNode>() {
            @Override
            public Optional<FileSystemNode> handleAsDescendantOfChild(VfsRelativePath pathInChild, FileSystemNode child) {
                return child.invalidate(pathInChild, caseSensitivity, diffListener);
            }

            @Override
            public void handleAsAncestorOfChild(String childPath, FileSystemNode child) {
                diffListener.nodeRemoved(child);
            }

            @Override
            public void handleExactMatchWithChild(FileSystemNode child) {
                diffListener.nodeRemoved(child);
            }

            @Override
            public void handleUnrelatedToAnyChild() {
            }
        });
        if (newChildren.isEmpty()) {
            return withAllChildrenRemoved();
        }
        if (newChildren == children) {
            return Optional.of(withIncompleteChildren());
        }
        return Optional.of(withIncompleteChildren(newChildren));
    }

    @Override
    public FileSystemNode store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.store(targetPath, caseSensitivity, new ChildMap.StoreHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleAsDescendantOfChild(VfsRelativePath pathInChild, FileSystemNode child) {
                return child.store(pathInChild, caseSensitivity, snapshot, diffListener);
            }

            @Override
            public FileSystemNode handleAsAncestorOfChild(String childPath, FileSystemNode child) {
                FileSystemNode newChild = snapshot.asFileSystemNode();
                diffListener.nodeRemoved(child);
                diffListener.nodeAdded(newChild);
                return newChild;
            }

            @Override
            public FileSystemNode mergeWithExisting(FileSystemNode child) {
                if (snapshot instanceof FileSystemLocationSnapshot || !child.getSnapshot().map(oldSnapshot -> oldSnapshot instanceof FileSystemLocationSnapshot).orElse(false)) {
                    FileSystemNode newChild = snapshot.asFileSystemNode();
                    diffListener.nodeRemoved(child);
                    diffListener.nodeAdded(newChild);
                    return newChild;
                } else {
                    return child;
                }
            }

            @Override
            public FileSystemNode createChild() {
                FileSystemNode newChild = snapshot.asFileSystemNode();
                diffListener.nodeAdded(newChild);
                return newChild;
            }

            @Override
            public FileSystemNode createNodeFromChildren(ChildMap<FileSystemNode> children) {
                boolean isDirectory = anyChildMatches(children, node -> node.getSnapshot().map(this::isRegularFileOrDirectory).orElse(false));
                return isDirectory ? new PartialDirectoryNode(children) : new UnknownFileSystemNode(children);
            }

            private boolean isRegularFileOrDirectory(MetadataSnapshot metadataSnapshot) {
                return metadataSnapshot.getType() != FileType.Missing;
            }
        });
        if (newChildren == children) {
            return this;
        }
        return withIncompleteChildren(newChildren);
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return SnapshotUtil.getMetadataFromChildren(children, targetPath, caseSensitivity, Optional::empty);
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
    protected abstract FileSystemNode withIncompleteChildren(ChildMap<? extends FileSystemNode> newChildren);

    /**
     * Returns an updated node with all children removed, or {@link Optional#empty()}
     * if the node without children would contain no useful information to keep around.
     */
    protected abstract Optional<FileSystemNode> withAllChildrenRemoved();

    @Override
    public Stream<FileSystemLocationSnapshot> rootSnapshots() {
        return children.stream()
            .map(ChildMap.Entry::getValue)
            .flatMap(FileSystemNode::rootSnapshots);
    }

    @Override
    public boolean hasDescendants() {
        return anyChildMatches(children, FileSystemNode::hasDescendants);
    }

    private static boolean anyChildMatches(ChildMap<FileSystemNode> children, Predicate<FileSystemNode> predicate) {
        return children.stream()
            .map(ChildMap.Entry::getValue)
            .anyMatch(predicate);
    }
}
