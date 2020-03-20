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
import org.gradle.internal.vfs.SnapshotHierarchy;

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

    public static FileSystemNode storeSingleChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.ChangeListener changeListener) {
        return handlePrefix(child.getPathToParent(), relativePath, caseSensitivity, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return child.store(
                    relativePath.fromChild(child.getPathToParent()),
                    caseSensitivity,
                    snapshot,
                    changeListener
                );
            }

            @Override
            public FileSystemNode handleParent() {
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
                changeListener.nodeRemoved(child);
                FileSystemNode newNode = snapshot.asFileSystemNode(relativePath.getAsString());
                changeListener.nodeAdded(newNode);
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

                changeListener.nodeAdded(sibling);

                boolean isDirectory = child.getSnapshot().filter(SnapshotUtil::isRegularFileOrDirectory).isPresent() || isRegularFileOrDirectory(snapshot);
                return isDirectory ? new PartialDirectorySnapshot(commonPrefix, newChildren) : new UnknownSnapshot(commonPrefix, newChildren);
            }
        });
    }

    private static boolean isRegularFileOrDirectory(MetadataSnapshot metadataSnapshot) {
        return metadataSnapshot.getType() != FileType.Missing;
    }

    public static Optional<FileSystemNode> invalidateSingleChild(FileSystemNode child, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.ChangeListener changeListener) {
        return handlePrefix(child.getPathToParent(), relativePath, caseSensitivity, new DescendantHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return child.invalidate(relativePath.fromChild(child.getPathToParent()), caseSensitivity, changeListener);
            }

            @Override
            public Optional<FileSystemNode> handleParent() {
                changeListener.nodeRemoved(child);
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleSame() {
                changeListener.nodeRemoved(child);
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

    private static <T> T handlePrefix(String prefix, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, DescendantHandler<T> descendantHandler) {
        int prefixLength = prefix.length();
        int pathLength = relativePath.length();
        int maxPos = Math.min(prefixLength, pathLength);
        int commonPrefixLength = relativePath.lengthOfCommonPrefix(prefix, caseSensitivity);
        if (commonPrefixLength == maxPos) {
            if (prefixLength > pathLength) {
                return descendantHandler.handleParent();
            }
            if (prefixLength == pathLength) {
                return descendantHandler.handleSame();
            }
            return descendantHandler.handleDescendant();
        }
        return descendantHandler.handleDifferent(commonPrefixLength);
    }

    private interface DescendantHandler<T> {
        T handleDescendant();
        T handleParent();
        T handleSame();
        T handleDifferent(int commonPrefixLength);
    }
}
