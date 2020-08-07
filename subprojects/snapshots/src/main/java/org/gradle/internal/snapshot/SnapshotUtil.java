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
import org.gradle.internal.file.FileType;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {
    /**
     * If a node has fewer children, we use a linear search for the child.
     * We use this limit since {@link VfsRelativePath#compareToFirstSegment(String, CaseSensitivity)}
     * is about twice as slow as {@link VfsRelativePath#hasPrefix(String, CaseSensitivity)},
     * so comparing the searched path to all of the children is actually faster than doing a binary search.
     */
    private static final int MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH = 10;

    public static Optional<MetadataSnapshot> getMetadataFromChildren(List<? extends FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        int numberOfChildren = children.size();
        switch (numberOfChildren) {
            case 0:
                return noChildFoundResult.get();
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return relativePath.hasPrefix(onlyChild.getPathToParent(), caseSensitivity)
                    ? getSnapshotFromChild(onlyChild, relativePath, caseSensitivity)
                    : noChildFoundResult.get();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (relativePath.hasPrefix(firstChild.getPathToParent(), caseSensitivity)) {
                    return getSnapshotFromChild(firstChild, relativePath, caseSensitivity);
                }
                if (relativePath.hasPrefix(secondChild.getPathToParent(), caseSensitivity)) {
                    return getSnapshotFromChild(secondChild, relativePath, caseSensitivity);
                }
                return noChildFoundResult.get();
            default:
                if (numberOfChildren < MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH) {
                    for (FileSystemNode currentChild : children) {
                        if (relativePath.hasPrefix(currentChild.getPathToParent(), caseSensitivity)) {
                            return getSnapshotFromChild(currentChild, relativePath, caseSensitivity);
                        }
                    }
                    return noChildFoundResult.get();
                } else {
                    int foundChild = SearchUtil.binarySearch(children, child -> relativePath.compareToFirstSegment(child.getPathToParent(), caseSensitivity));
                    return (foundChild >= 0 && relativePath.hasPrefix(children.get(foundChild).getPathToParent(), caseSensitivity))
                        ? getSnapshotFromChild(children.get(foundChild), relativePath, caseSensitivity)
                        : noChildFoundResult.get();
                }
        }
    }

    public static Optional<MetadataSnapshot> getSnapshotFromChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        if (relativePath.length() == child.getPathToParent().length()) {
            return child.getSnapshot();
        }
        return child.getSnapshot(relativePath.fromChild(child.getPathToParent()), caseSensitivity);
    }

    public static ReadOnlyFileSystemNode getChild(List<? extends FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        int numberOfChildren = children.size();
        switch (numberOfChildren) {
            case 0:
                return ReadOnlyFileSystemNode.EMPTY;
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return getNodeFromChild(onlyChild, relativePath, caseSensitivity)
                    .orElse(ReadOnlyFileSystemNode.EMPTY);
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                return getNodeFromChild(firstChild, relativePath, caseSensitivity)
                    .orElseGet(() -> getNodeFromChild(secondChild, relativePath, caseSensitivity).orElse(ReadOnlyFileSystemNode.EMPTY));
            default:
                if (numberOfChildren < MINIMUM_CHILD_COUNT_FOR_BINARY_SEARCH) {
                    for (FileSystemNode currentChild : children) {
                        Optional<ReadOnlyFileSystemNode> node = getNodeFromChild(currentChild, relativePath, caseSensitivity);
                        if (node.isPresent()) {
                            return node.get();
                        }
                    }
                    return ReadOnlyFileSystemNode.EMPTY;
                } else {
                    int foundChild = SearchUtil.binarySearch(children, child -> relativePath.compareToFirstSegment(child.getPathToParent(), caseSensitivity));
                    if (foundChild >= 0) {
                        return getNodeFromChild(children.get(foundChild), relativePath, caseSensitivity).orElse(ReadOnlyFileSystemNode.EMPTY);
                    }
                    return ReadOnlyFileSystemNode.EMPTY;
                }
        }
    }

    public static Optional<ReadOnlyFileSystemNode> getNodeFromChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return handlePathRelationship(child.getPathToParent(), relativePath, caseSensitivity, new PathRelationshipHandler<Optional<ReadOnlyFileSystemNode>>() {
            @Override
            public Optional<ReadOnlyFileSystemNode> handleDescendant() {
                return Optional.of(child.getNode(relativePath.fromChild(child.getPathToParent()), caseSensitivity));
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleAncestor() {
                return Optional.of(child.withPathToParent(child.getPathToParent().substring(relativePath.length() + 1)));
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleSame() {
                return Optional.of(child);
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleDifferent(int commonPrefixLength) {
                return Optional.empty();
            }
        });
    }

    public static FileSystemNode storeSingleChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        return handlePathRelationship(child.getPathToParent(), relativePath, caseSensitivity, new PathRelationshipHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return child.store(
                    relativePath.fromChild(child.getPathToParent()),
                    caseSensitivity,
                    snapshot,
                    diffListener
                );
            }

            @Override
            public FileSystemNode handleAncestor() {
                return replacedNode();
            }

            @Override
            public FileSystemNode handleSame() {
                return snapshot instanceof CompleteFileSystemLocationSnapshot
                    ? replacedNode()
                    : child.getSnapshot()
                        .filter(oldSnapshot -> oldSnapshot instanceof CompleteFileSystemLocationSnapshot)
                        .map(it -> child)
                        .orElseGet(this::replacedNode);
            }

            private FileSystemNode replacedNode() {
                diffListener.nodeRemoved(child);
                FileSystemNode newNode = snapshot.asFileSystemNode(relativePath.getAsString());
                diffListener.nodeAdded(newNode);
                return newNode;
            }

            @Override
            public FileSystemNode handleDifferent(int commonPrefixLength) {
                String prefix = child.getPathToParent();
                String commonPrefix = prefix.substring(0, commonPrefixLength);
                boolean emptyCommonPrefix = commonPrefixLength == 0;
                FileSystemNode newChild = emptyCommonPrefix ? child : child.withPathToParent(prefix.substring(commonPrefixLength + 1));
                FileSystemNode sibling = snapshot.asFileSystemNode(emptyCommonPrefix ? relativePath.getAsString() : relativePath.suffixStartingFrom(commonPrefixLength + 1).getAsString());
                ImmutableList<FileSystemNode> newChildren = PathUtil.getPathComparator(caseSensitivity).compare(newChild.getPathToParent(), sibling.getPathToParent()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild);

                diffListener.nodeAdded(sibling);

                boolean isDirectory = child.getSnapshot().filter(SnapshotUtil::isRegularFileOrDirectory).isPresent() || isRegularFileOrDirectory(snapshot);
                return isDirectory ? new PartialDirectorySnapshot(commonPrefix, newChildren) : new UnknownSnapshot(commonPrefix, newChildren);
            }
        });
    }

    private static boolean isRegularFileOrDirectory(MetadataSnapshot metadataSnapshot) {
        return metadataSnapshot.getType() != FileType.Missing;
    }

    public static Optional<FileSystemNode> invalidateSingleChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        return handlePathRelationship(child.getPathToParent(), relativePath, caseSensitivity, new PathRelationshipHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return child.invalidate(relativePath.fromChild(child.getPathToParent()), caseSensitivity, diffListener);
            }

            @Override
            public Optional<FileSystemNode> handleAncestor() {
                diffListener.nodeRemoved(child);
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleSame() {
                diffListener.nodeRemoved(child);
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleDifferent(int commonPrefixLength) {
                return Optional.of(child);
            }
        });
    }

    public static <T> T handleChildren(List<? extends FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, ChildHandler<T> childHandler) {
        int childIndex = SearchUtil.binarySearch(
            children,
            candidate -> relativePath.compareToFirstSegment(candidate.getPathToParent(), caseSensitivity)
        );
        if (childIndex >= 0) {
            return childHandler.handleChildOfExisting(childIndex);
        }
        return childHandler.handleNewChild(-childIndex - 1);
    }

    public interface ChildHandler<T> {
        T handleNewChild(int insertBefore);
        T handleChildOfExisting(int childIndex);
    }

    /**
     * Handles the relationship between two relative paths, pathToParent and relativePath.
     *
     * Typically, pathToParent is the path from the current node to one of its children,
     * and relativePath is the path we are trying to look up in the children.
     */
    private static <T> T handlePathRelationship(String pathToParent, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<T> pathRelationshipHandler) {
        int pathToParentLength = pathToParent.length();
        int relativePathLength = relativePath.length();
        int maxPos = Math.min(pathToParentLength, relativePathLength);
        int commonPrefixLength = relativePath.lengthOfCommonPrefix(pathToParent, caseSensitivity);
        if (commonPrefixLength == maxPos) {
            if (pathToParentLength > relativePathLength) {
                return pathRelationshipHandler.handleAncestor();
            }
            if (pathToParentLength == relativePathLength) {
                return pathRelationshipHandler.handleSame();
            }
            return pathRelationshipHandler.handleDescendant();
        }
        return pathRelationshipHandler.handleDifferent(commonPrefixLength);
    }

    private interface PathRelationshipHandler<T> {
        /**
         * relativePath is a descendant of pathToParent.
         */
        T handleDescendant();
        /**
         * relativePath is an ancestor of pathToParent.
         */
        T handleAncestor();
        /**
         * relativePath is the same as pathToParent.
         */
        T handleSame();
        /**
         * relativePath may have a common prefix with pathToParent,
         * but the common prefix is different to both pathToParent and relativePath.
         */
        T handleDifferent(int commonPrefixLength);
    }
}
