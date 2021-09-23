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
import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.gradle.internal.snapshot.ChildMapFactory.childMapFromSorted;
import static org.gradle.internal.snapshot.SnapshotVisitResult.CONTINUE;

/**
 * A snapshot of an existing directory hierarchy.
 *
 * Includes snapshots of any child element and the Merkle tree hash.
 */
public class DirectorySnapshot extends AbstractFileSystemLocationSnapshot {
    private final ChildMap<FileSystemLocationSnapshot> children;
    private final HashCode contentHash;

    public DirectorySnapshot(String absolutePath, String name, AccessType accessType, HashCode contentHash, List<FileSystemLocationSnapshot> children) {
        this(absolutePath, name, accessType, contentHash, childMapFromSorted(children.stream()
            .map(it -> new ChildMap.Entry<>(it.getName(), it))
            .collect(Collectors.toList())));
    }

    public DirectorySnapshot(String absolutePath, String name, AccessType accessType, HashCode contentHash, ChildMap<FileSystemLocationSnapshot> children) {
        super(absolutePath, name, accessType);
        this.contentHash = contentHash;
        this.children = children;
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
        return isContentUpToDate(other);
    }

    @Override
    public boolean isContentUpToDate(FileSystemLocationSnapshot other) {
        return other instanceof DirectorySnapshot;
    }

    @Override
    public SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
        SnapshotVisitResult result = visitor.visitEntry(this);
        switch (result) {
            case CONTINUE:
                visitor.enterDirectory(this);
                children.stream()
                    .map(ChildMap.Entry::getValue)
                    .forEach(child -> child.accept(visitor));
                visitor.leaveDirectory(this);
                return CONTINUE;
            case SKIP_SUBTREE:
                return CONTINUE;
            case TERMINATE:
                return SnapshotVisitResult.TERMINATE;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor) {
        pathTracker.enter(getName());
        try {
            SnapshotVisitResult result = visitor.visitEntry(this, pathTracker);
            switch (result) {
                case CONTINUE:
                    visitor.enterDirectory(this, pathTracker);
                    children.stream()
                        .map(ChildMap.Entry::getValue)
                        .forEach(child -> child.accept(pathTracker, visitor));
                    visitor.leaveDirectory(this, pathTracker);
                    return CONTINUE;
                case SKIP_SUBTREE:
                    return CONTINUE;
                case TERMINATE:
                    return SnapshotVisitResult.TERMINATE;
                default:
                    throw new AssertionError();
            }
        } finally {
            pathTracker.leave();
        }
    }

    @Override
    public void accept(FileSystemLocationSnapshotVisitor visitor) {
        visitor.visitDirectory(this);
    }

    @Override
    public <T> T accept(FileSystemLocationSnapshotTransformer<T> transformer) {
        return transformer.visitDirectory(this);
    }

    @VisibleForTesting
    public ImmutableList<FileSystemLocationSnapshot> getChildren() {
        return children.stream()
            .map(ChildMap.Entry::getValue)
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    protected Optional<MetadataSnapshot> getChildSnapshot(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return Optional.of(
            SnapshotUtil.getMetadataFromChildren(children, targetPath, caseSensitivity, Optional::empty)
                .orElseGet(() -> missingSnapshotForAbsolutePath(targetPath.getAbsolutePath()))
        );
    }

    @Override
    protected ReadOnlyFileSystemNode getChildNode(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        ReadOnlyFileSystemNode childNode = SnapshotUtil.getChild(children, targetPath, caseSensitivity);
        return childNode == ReadOnlyFileSystemNode.EMPTY
            ? missingSnapshotForAbsolutePath(targetPath.getAbsolutePath())
            : childNode;
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        ChildMap<FileSystemNode> newChildren = children.invalidate(targetPath, caseSensitivity, new ChildMap.InvalidationHandler<FileSystemLocationSnapshot, FileSystemNode>() {
            @Override
            public Optional<FileSystemNode> handleAsDescendantOfChild(VfsRelativePath pathInChild, FileSystemLocationSnapshot child) {
                diffListener.nodeRemoved(DirectorySnapshot.this);
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
                children.stream()
                    .map(ChildMap.Entry::getValue)
                    .filter(existingChild -> existingChild != child)
                    .forEach(diffListener::nodeAdded);
                return invalidated;
            }

            @Override
            public void handleAsAncestorOfChild(String childPath, FileSystemLocationSnapshot child) {
                throw new IllegalStateException("Can't have an ancestor of a single path element");
            }

            @Override
            public void handleExactMatchWithChild(FileSystemLocationSnapshot child) {
                diffListener.nodeRemoved(DirectorySnapshot.this);
                children.stream()
                    .map(ChildMap.Entry::getValue)
                    .filter(existingChild -> existingChild != child)
                    .forEach(diffListener::nodeAdded);
            }

            @Override
            public void handleUnrelatedToAnyChild() {
                diffListener.nodeRemoved(DirectorySnapshot.this);
                children.stream()
                    .map(ChildMap.Entry::getValue)
                    .forEach(diffListener::nodeAdded);
            }
        });
        return Optional.of(new PartialDirectoryNode(newChildren));
    }

    @Override
    public String toString() {
        return String.format("%s@%s/%s(%s)", super.toString(), contentHash, getName(), children);
    }
}
