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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {
    /**
     * If a node has fewer children, we use a linear search for the child.
     * We use this limit since {@link PathUtil#compareToChildOfOrThis(String, String, int, CaseSensitivity)}
     * is about twice as slow as {@link PathUtil#isChildOfOrThis(String, String, int, CaseSensitivity)},
     * so comparing the searched path to all of the children is actually faster than doing a binary search.
     */
    private static final int MINIMUM_CHILD_NUMBER_FOR_BINARY_SEARCH = 10;

    public static Optional<MetadataSnapshot> getMetadataFromChildren(List<? extends FileSystemNode> children, String filePath, int offset, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        int numberOfChildren = children.size();
        switch (numberOfChildren) {
            case 0:
                return noChildFoundResult.get();
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return PathUtil.isChildOfOrThis(onlyChild.getPathToParent(), filePath, offset, caseSensitivity)
                    ? getSnapshotFromChild(filePath, offset, onlyChild, caseSensitivity)
                    : noChildFoundResult.get();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (PathUtil.isChildOfOrThis(firstChild.getPathToParent(), filePath, offset, caseSensitivity)) {
                    return getSnapshotFromChild(filePath, offset, firstChild, caseSensitivity);
                }
                if (PathUtil.isChildOfOrThis(secondChild.getPathToParent(), filePath, offset, caseSensitivity)) {
                    return getSnapshotFromChild(filePath, offset, secondChild, caseSensitivity);
                }
                return noChildFoundResult.get();
            default:
                if (numberOfChildren < MINIMUM_CHILD_NUMBER_FOR_BINARY_SEARCH) {
                    for (FileSystemNode currentChild : children) {
                        if (PathUtil.isChildOfOrThis(currentChild.getPathToParent(), filePath, offset, caseSensitivity)) {
                            return getSnapshotFromChild(filePath, offset, currentChild, caseSensitivity);
                        }
                    }
                    return noChildFoundResult.get();
                } else {
                    int foundChild = SearchUtil.binarySearch(children, child -> PathUtil.compareToChildOfOrThis(child.getPathToParent(), filePath, offset, caseSensitivity));
                    return foundChild >= 0
                        ? getSnapshotFromChild(filePath, offset, children.get(foundChild), caseSensitivity)
                        : noChildFoundResult.get();
                }
        }
    }

    private static Optional<MetadataSnapshot> getSnapshotFromChild(String filePath, int offset, FileSystemNode child, CaseSensitivity caseSensitivity) {
        return child.getSnapshot(filePath, offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()), caseSensitivity);
    }

    public static FileSystemNode storeSingleChild(FileSystemNode child, String path, int offset, MetadataSnapshot snapshot, CaseSensitivity caseSensitivity) {
        return handlePrefix(child.getPathToParent(), path, offset, caseSensitivity, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return child.store(
                    path,
                    offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()),
                    caseSensitivity,
                    snapshot);
            }

            @Override
            public FileSystemNode handleParent() {
                return snapshot.withPathToParent(path.substring(offset));
            }

            @Override
            public FileSystemNode handleSame() {
                return snapshot instanceof CompleteFileSystemLocationSnapshot
                    ? snapshot.withPathToParent(child.getPathToParent())
                    : child.getSnapshot(path, path.length() + 1, caseSensitivity)
                        .filter(oldSnapshot -> oldSnapshot instanceof CompleteFileSystemLocationSnapshot)
                        .map(FileSystemNode.class::cast)
                        .orElse(snapshot.withPathToParent(child.getPathToParent()));
            }

            @Override
            public FileSystemNode handleDifferent(int commonPrefixLength) {
                String prefix = child.getPathToParent();
                String commonPrefix = prefix.substring(0, commonPrefixLength);
                boolean emptyCommonPrefix = commonPrefixLength == 0;
                FileSystemNode newChild = emptyCommonPrefix ? child : child.withPathToParent(prefix.substring(commonPrefixLength + 1));
                FileSystemNode sibling = snapshot.withPathToParent(emptyCommonPrefix ? path.substring(offset) : path.substring(offset + commonPrefixLength + 1));
                ImmutableList<FileSystemNode> newChildren = PathUtil.getPathComparator(caseSensitivity).compare(newChild.getPathToParent(), sibling.getPathToParent()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild);
                boolean isDirectory = isRegularFileOrDirectory(child) || isRegularFileOrDirectory(snapshot);
                return isDirectory ? new PartialDirectorySnapshot(commonPrefix, newChildren) : new UnknownSnapshot(commonPrefix, newChildren);
            }
        });
    }

    private static boolean isRegularFileOrDirectory(FileSystemNode node) {
        return (node instanceof MetadataSnapshot) && ((MetadataSnapshot) node).getType() != FileType.Missing;
    }

    public static Optional<FileSystemNode> invalidateSingleChild(FileSystemNode child, String path, int offset, CaseSensitivity caseSensitivity) {
        return handlePrefix(child.getPathToParent(), path, offset, caseSensitivity, new DescendantHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return child.invalidate(path, offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()), caseSensitivity);
            }

            @Override
            public Optional<FileSystemNode> handleParent() {
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleSame() {
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleDifferent(int commonPrefixLength) {
                return Optional.of(child);
            }
        });
    }

    public static Optional<MetadataSnapshot> thisOrGet(@Nullable MetadataSnapshot current, String filePath, int offset, Supplier<Optional<MetadataSnapshot>> supplier) {
        if (filePath.length() + 1 == offset) {
            return Optional.ofNullable(current);
        }
        return supplier.get();
    }

    public static MissingFileSnapshot missingSnapshotForAbsolutePath(String filePath) {
        return new MissingFileSnapshot(filePath);
    }

    public static <T> T handleChildren(List<? extends FileSystemNode> children, String path, int offset, CaseSensitivity caseSensitivity, ChildHandler<T> childHandler) {
        int childIndex = SearchUtil.binarySearch(
            children,
            candidate -> PathUtil.compareWithCommonPrefix(candidate.getPathToParent(), path, offset, caseSensitivity)
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

    public static <T> T handlePrefix(String prefix, String path, int offset, CaseSensitivity caseSensitivity, DescendantHandler<T> descendantHandler) {
        int prefixLength = prefix.length();
        int pathLength = path.length() - offset;
        int maxPos = Math.min(prefixLength, pathLength);
        int commonPrefixLength = PathUtil.sizeOfCommonPrefix(prefix, path, offset, caseSensitivity);
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

    public interface DescendantHandler<T> {
        T handleDescendant();
        T handleParent();
        T handleSame();
        T handleDifferent(int commonPrefixLength);
    }
}
