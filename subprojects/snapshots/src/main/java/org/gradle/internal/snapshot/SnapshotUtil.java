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

    public static Optional<MetadataSnapshot> getMetadataFromChildren(List<? extends FileSystemNode> children, String filePath, int offset, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        switch (children.size()) {
            case 0:
                return noChildFoundResult.get();
            case 1:
                FileSystemNode onlyChild = children.get(0);
                return PathUtil.isChildOfOrThis(filePath, offset, onlyChild.getPathToParent())
                    ? getSnapshotFromChild(filePath, offset, onlyChild)
                    : noChildFoundResult.get();
            case 2:
                FileSystemNode firstChild = children.get(0);
                FileSystemNode secondChild = children.get(1);
                if (PathUtil.isChildOfOrThis(filePath, offset, firstChild.getPathToParent())) {
                    return getSnapshotFromChild(filePath, offset, firstChild);
                }
                if (PathUtil.isChildOfOrThis(filePath, offset, secondChild.getPathToParent())) {
                    return getSnapshotFromChild(filePath, offset, secondChild);
                }
                return noChildFoundResult.get();
            default:
                int foundChild = SearchUtil.binarySearch(children, child -> PathUtil.compareToChildOfOrThis(child.getPathToParent(), filePath, offset));
                return foundChild >= 0
                    ? getSnapshotFromChild(filePath, offset, children.get(foundChild))
                    : noChildFoundResult.get();
        }
    }

    private static Optional<MetadataSnapshot> getSnapshotFromChild(String filePath, int offset, FileSystemNode child) {
        return child.getSnapshot(filePath, offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()));
    }

    public static FileSystemNode storeSingleChild(FileSystemNode child, String path, int offset, MetadataSnapshot snapshot) {
        return handlePrefix(child.getPathToParent(), path, offset, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return child.store(
                    path,
                    offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()),
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
                    : child.getSnapshot(path, path.length() + 1)
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
                ImmutableList<FileSystemNode> newChildren = PathUtil.pathComparator().compare(newChild.getPathToParent(), sibling.getPathToParent()) < 0
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

    public static Optional<FileSystemNode> invalidateSingleChild(FileSystemNode child, String path, int offset) {
        return handlePrefix(child.getPathToParent(), path, offset, new DescendantHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return child.invalidate(path, offset + child.getPathToParent().length() + PathUtil.descendantChildOffset(child.getPathToParent()));
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

    public static <T> T handleChildren(List<? extends FileSystemNode> children, String path, int offset, ChildHandler<T> childHandler) {
        int childIndex = SearchUtil.binarySearch(
            children,
            candidate -> PathUtil.compareWithCommonPrefix(candidate.getPathToParent(), path, offset)
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

    public static <T> T handlePrefix(String prefix, String path, int offset, DescendantHandler<T> descendantHandler) {
        int prefixLength = prefix.length();
        int pathLength = path.length() - offset;
        int maxPos = Math.min(prefixLength, pathLength);
        int commonPrefixLength = PathUtil.sizeOfCommonPrefix(prefix, path, offset);
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
