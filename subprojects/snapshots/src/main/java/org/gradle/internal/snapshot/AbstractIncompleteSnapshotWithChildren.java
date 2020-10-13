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

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractIncompleteSnapshotWithChildren implements FileSystemNode {
    protected final ChildMap<FileSystemNode> children;

    @SuppressWarnings("unchecked")
    public AbstractIncompleteSnapshotWithChildren(ChildMap<? extends FileSystemNode> children) {
        this.children = (ChildMap<FileSystemNode>) children;
    }

    @Override
    public ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return SnapshotUtil.getChild(children, relativePath, caseSensitivity);
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.invalidate(relativePath, caseSensitivity, new ChildMap.InvalidationHandler<FileSystemNode, FileSystemNode>() {
            @Override
            public Optional<FileSystemNode> invalidateDescendantOfChild(VfsRelativePath pathInChild, FileSystemNode child) {
                return child.invalidate(pathInChild, caseSensitivity, diffListener);
            }

            @Override
            public void ancestorInvalidated(FileSystemNode child) {
                diffListener.nodeRemoved(child);
            }

            @Override
            public void childInvalidated(FileSystemNode child) {
                diffListener.nodeRemoved(child);
            }

            @Override
            public void invalidatedChildNotFound() {
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
    public FileSystemNode store(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.store(relativePath, caseSensitivity, new ChildMap.StoreHandler<FileSystemNode>() {
            @Override
            public FileSystemNode storeInChild(VfsRelativePath pathInChild, FileSystemNode child) {
                return child.store(pathInChild, caseSensitivity, snapshot, diffListener);
            }

            @Override
            public FileSystemNode storeAsAncestor(VfsRelativePath pathToChild, FileSystemNode child) {
                FileSystemNode newChild = snapshot.asFileSystemNode();
                diffListener.nodeRemoved(child);
                diffListener.nodeAdded(newChild);
                return newChild;
            }

            @Override
            public FileSystemNode mergeWithExisting(FileSystemNode child) {
                if (snapshot instanceof CompleteFileSystemLocationSnapshot || !child.getSnapshot().map(oldSnapshot -> oldSnapshot instanceof CompleteFileSystemLocationSnapshot).orElse(false)) {
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
                AtomicBoolean isDirectory = new AtomicBoolean(false);
                children.visitChildren((__, child) -> child.getSnapshot().map(this::isRegularFileOrDirectory).ifPresent(notMissing -> isDirectory.compareAndSet(false, notMissing)));
                return isDirectory.get() ? new PartialDirectorySnapshot(children) : new UnknownSnapshot(children);
            }

            @Override
            public Comparator<String> getPathComparator() {
                return PathUtil.getPathComparator(caseSensitivity);
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
    public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return SnapshotUtil.getMetadataFromChildren(children, relativePath, caseSensitivity, Optional::empty);
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
    public void accept(SnapshotHierarchy.SnapshotVisitor snapshotVisitor) {
        children.visitChildren((childPath, child) -> child.accept(snapshotVisitor));
    }

    @Override
    public boolean hasDescendants() {
        AtomicBoolean hasDescendants = new AtomicBoolean();
        children.visitChildren((childPath, child) -> hasDescendants.compareAndSet(false, child.hasDescendants()));
        return hasDescendants.get();
    }
}
