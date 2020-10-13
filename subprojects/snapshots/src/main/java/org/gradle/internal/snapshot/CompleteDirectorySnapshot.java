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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A complete snapshot of an existing directory.
 *
 * Includes complete snapshots of every child and the Merkle tree hash.
 */
public class CompleteDirectorySnapshot extends AbstractCompleteFileSystemLocationSnapshot {
    private final ChildMap<CompleteFileSystemLocationSnapshot> children;
    private final HashCode contentHash;

    public CompleteDirectorySnapshot(String absolutePath, String name, List<CompleteFileSystemLocationSnapshot> children, HashCode contentHash, AccessType accessType) {
        this(
            absolutePath,
            name,
            ChildMap.of(children.stream()
                .map(it -> new ChildMap.Entry<>(it.getName(), it))
                .collect(Collectors.toList())),
            contentHash,
            accessType
        );
    }

    public CompleteDirectorySnapshot(String absolutePath, String name, ChildMap<CompleteFileSystemLocationSnapshot> children, HashCode contentHash, AccessType accessType) {
        super(absolutePath, name, accessType);
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
    public boolean isContentAndMetadataUpToDate(CompleteFileSystemLocationSnapshot other) {
        return other instanceof CompleteDirectorySnapshot;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        if (!visitor.preVisitDirectory(this)) {
            return;
        }
        children.visitChildren((__, child) -> child.accept(visitor));
        visitor.postVisitDirectory(this);
    }

    @VisibleForTesting
    public List<CompleteFileSystemLocationSnapshot> getChildren() {
        return children.values();
    }

    @Override
    protected Optional<MetadataSnapshot> getChildSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return Optional.of(
            SnapshotUtil.getMetadataFromChildren(children, relativePath, caseSensitivity, Optional::empty)
                .orElseGet(() -> missingSnapshotForAbsolutePath(relativePath.getAbsolutePath()))
        );
    }

    @Override
    protected ReadOnlyFileSystemNode getChildNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        ReadOnlyFileSystemNode childNode = SnapshotUtil.getChild(children, relativePath, caseSensitivity);
        return childNode == ReadOnlyFileSystemNode.EMPTY
            ? missingSnapshotForAbsolutePath(relativePath.getAbsolutePath())
            : childNode;
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.invalidate(relativePath, caseSensitivity, new ChildMap.InvalidationHandler<CompleteFileSystemLocationSnapshot, FileSystemNode>() {
            @Override
            public Optional<FileSystemNode> invalidateDescendantOfChild(VfsRelativePath pathInChild, CompleteFileSystemLocationSnapshot child) {
                diffListener.nodeRemoved(CompleteDirectorySnapshot.this);
                Optional<FileSystemNode> invalidated = child.invalidate(pathInChild, caseSensitivity, new SnapshotHierarchy.NodeDiffListener() {
                    @Override
                    public void nodeRemoved(FileSystemNode node) {
                        // the parent already has been removed. No children need to be removed.
                    }

                    @Override
                    public void nodeAdded(FileSystemNode node) {
                        diffListener.nodeAdded(node);
                    }
                });
                children.visitChildren((__, existingChild) -> {
                    if (existingChild != child) {
                        diffListener.nodeAdded(existingChild);
                    }
                });
                return invalidated;
            }

            @Override
            public void ancestorInvalidated(CompleteFileSystemLocationSnapshot child) {
                throw new IllegalStateException("Can't have an ancestor of a single path element");
            }

            @Override
            public void childInvalidated(CompleteFileSystemLocationSnapshot child) {
                diffListener.nodeRemoved(CompleteDirectorySnapshot.this);
                children.visitChildren((__, existingChild) -> {
                    if (existingChild != child) {
                        diffListener.nodeAdded(existingChild);
                    }
                });
            }

            @Override
            public void invalidatedChildNotFound() {
                diffListener.nodeRemoved(CompleteDirectorySnapshot.this);
                children.visitChildren((__, child) -> diffListener.nodeAdded(child));
            }
        });
        return Optional.of(new PartialDirectorySnapshot(newChildren));
    }
}
