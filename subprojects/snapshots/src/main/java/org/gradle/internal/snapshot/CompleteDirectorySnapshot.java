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
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A complete snapshot of an existing directory.
 *
 * Includes complete snapshots of every child and the Merkle tree hash.
 */
public class CompleteDirectorySnapshot extends AbstractCompleteFileSystemLocationSnapshot {
    private final List<CompleteFileSystemLocationSnapshot> children;
    private final HashCode contentHash;

    public CompleteDirectorySnapshot(String absolutePath, String name, List<CompleteFileSystemLocationSnapshot> children, HashCode contentHash) {
        super(absolutePath, name);
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
        for (CompleteFileSystemLocationSnapshot child : children) {
            child.accept(visitor);
        }
        visitor.postVisitDirectory(this);
    }

    @Override
    public void accept(NodeVisitor visitor, boolean parentIsComplete) {
        visitor.visitNode(this, !parentIsComplete);
        for (CompleteFileSystemLocationSnapshot child : children) {
            child.accept(visitor, true);
        }
    }

    @VisibleForTesting
    public List<CompleteFileSystemLocationSnapshot> getChildren() {
        return children;
    }

    @Override
    protected Optional<MetadataSnapshot> getChildSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return Optional.of(
            SnapshotUtil.getMetadataFromChildren(children, relativePath, caseSensitivity, Optional::empty)
                .orElseGet(() -> missingSnapshotForAbsolutePath(relativePath.getAbsolutePath()))
        );
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return SnapshotUtil.handleChildren(children, relativePath, caseSensitivity, new SnapshotUtil.ChildHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleNewChild(int insertBefore) {
                return Optional.of(new PartialDirectorySnapshot(getPathToParent(), children));
            }

            @Override
            public Optional<FileSystemNode> handleChildOfExisting(int childIndex) {
                CompleteFileSystemLocationSnapshot foundChild = children.get(childIndex);
                int childPathLength = foundChild.getPathToParent().length();
                Optional<FileSystemNode> invalidated = childPathLength == relativePath.length()
                    ? Optional.empty()
                    : foundChild.invalidate(relativePath.suffixStartingFrom(childPathLength + 1), caseSensitivity);
                return Optional.of(new PartialDirectorySnapshot(getPathToParent(), getChildren(childIndex, invalidated)));
            }

            private List<FileSystemNode> getChildren(int childIndex, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<FileSystemNode> invalidated) {
                if (children.size() == 1) {
                    return invalidated.map(ImmutableList::of).orElseGet(ImmutableList::of);
                }

                return invalidated
                    .map(invalidatedChild -> {
                        List<FileSystemNode> newChildren = new ArrayList<>(children);
                        newChildren.set(childIndex, invalidatedChild);
                        return newChildren;
                    }).orElseGet(() -> {
                        if (children.size() == 2) {
                            CompleteFileSystemLocationSnapshot singleChild = childIndex == 0 ? children.get(1) : children.get(0);
                            return ImmutableList.of(singleChild);
                        }
                        List<FileSystemNode> newChildren = new ArrayList<>(children);
                        newChildren.remove(childIndex);
                        return newChildren;
                    });
            }
        });
    }
}
