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
import org.gradle.internal.snapshot.children.ChildMap;
import org.gradle.internal.snapshot.children.DefaultChildMap;

import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {

    public static <T extends FileSystemNode> Optional<MetadataSnapshot> getMetadataFromChildren(ChildMap<T> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        return children.handlePath(relativePath, caseSensitivity, new ChildMap.PathRelationshipHandler<Optional<MetadataSnapshot>>() {
            @Override
            public Optional<MetadataSnapshot> handleDescendant(String childPath, int childIndex) {
                return children.get(childIndex).getSnapshot(relativePath.fromChild(childPath), caseSensitivity);
            }

            @Override
            public Optional<MetadataSnapshot> handleAncestor(String childPath, int childIndex) {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleSame(int childIndex) {
                return children.get(childIndex).getSnapshot();
            }

            @Override
            public Optional<MetadataSnapshot> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleDifferent(int indexOfNextBiggerChild) {
                return noChildFoundResult.get();
            }
        });
    }

    public static <T extends FileSystemNode> ReadOnlyFileSystemNode getChild(ChildMap<T> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return children.handlePath(relativePath, caseSensitivity, new ChildMap.PathRelationshipHandler<ReadOnlyFileSystemNode>() {
            @Override
            public ReadOnlyFileSystemNode handleDescendant(String childPath, int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return child.getNode(relativePath.fromChild(childPath), caseSensitivity);
            }

            @Override
            public ReadOnlyFileSystemNode handleAncestor(String childPath, int childIndex) {
                return children.get(childIndex);
            }

            @Override
            public ReadOnlyFileSystemNode handleSame(int childIndex) {
                return children.get(childIndex);
            }

            @Override
            public ReadOnlyFileSystemNode handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return ReadOnlyFileSystemNode.EMPTY;
            }

            @Override
            public ReadOnlyFileSystemNode handleDifferent(int indexOfNextBiggerChild) {
                return ReadOnlyFileSystemNode.EMPTY;
            }
        });
    }

    public static Optional<ReadOnlyFileSystemNode> getNodeFromChildren(ChildMap<FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return children.handlePath(relativePath, caseSensitivity, new ChildMap.PathRelationshipHandler<Optional<ReadOnlyFileSystemNode>>() {
            @Override
            public Optional<ReadOnlyFileSystemNode> handleDescendant(String childPath, int childIndex) {
                FileSystemNode child = children.get(childIndex);
                return Optional.of(child.getNode(relativePath.fromChild(childPath), caseSensitivity));
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleAncestor(String childPath, int childIndex) {
                return Optional.of(children.get(childIndex));
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleSame(int childIndex) {
                return Optional.of(children.get(childIndex));
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return Optional.empty();
            }

            @Override
            public Optional<ReadOnlyFileSystemNode> handleDifferent(int indexOfNextBiggerChild) {
                return Optional.empty();
            }
        });
    }

    public static ChildMap<FileSystemNode> storeSnapshot(ChildMap<FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener) {
        return children.handlePath(relativePath, caseSensitivity, new ChildMap.PathRelationshipHandler<ChildMap<FileSystemNode>>() {
            @Override
            public ChildMap<FileSystemNode> handleDescendant(String childPath, int childIndex) {
                FileSystemNode oldChild = children.get(childIndex);
                FileSystemNode newChild = oldChild.store(relativePath.fromChild(childPath), caseSensitivity, snapshot, diffListener);
                return replacedChild(childIndex, childPath, childPath, newChild, false);
            }

            @Override
            public ChildMap<FileSystemNode> handleAncestor(String childPath, int childIndex) {
                return replacedChild(childIndex, childPath, relativePath.getAsString(), snapshot.asFileSystemNode(), true);
            }

            @Override
            public ChildMap<FileSystemNode> handleSame(int childIndex) {
                FileSystemNode oldChild = children.get(childIndex);
                FileSystemNode newChild = mergeSnapshotWithNode(relativePath, snapshot, oldChild);
                return replacedChild(childIndex, relativePath.getAsString(), relativePath.getAsString(), newChild, true);
            }

            private ChildMap<FileSystemNode> replacedChild(int childIndex, String oldChildPath, String newChildPath, FileSystemNode newChild, boolean notifyListener) {
                FileSystemNode oldChild = children.get(childIndex);
                if (oldChildPath.equals(newChildPath) && oldChild.equals(newChild)) {
                    return children;
                }
                if (notifyListener) {
                    diffListener.nodeRemoved(oldChild);
                    diffListener.nodeAdded(newChild);
                }
                return children.withReplacedChild(
                    childIndex,
                    newChildPath,
                    newChild
                );
            }

            @Override
            public ChildMap<FileSystemNode> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                FileSystemNode oldChild = children.get(childIndex);
                String commonPrefix = childPath.substring(0, commonPrefixLength);
                String newChildPath = childPath.substring(commonPrefixLength + 1);
                ChildMap.Entry<FileSystemNode> newChild = new ChildMap.Entry<>(newChildPath, oldChild);
                String siblingPath = relativePath.suffixStartingFrom(commonPrefixLength + 1).getAsString();
                ChildMap.Entry<FileSystemNode> sibling = new ChildMap.Entry<>(siblingPath, snapshot.asFileSystemNode());
                DefaultChildMap<FileSystemNode> newChildren = new DefaultChildMap<>(PathUtil.getPathComparator(caseSensitivity).compare(newChild.getPath(), sibling.getPath()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild)
                );

                diffListener.nodeAdded(sibling.getValue());

                boolean isDirectory = oldChild.getSnapshot().filter(SnapshotUtil::isRegularFileOrDirectory).isPresent() || SnapshotUtil.isRegularFileOrDirectory(snapshot);
                return children.withReplacedChild(
                        childIndex,
                        commonPrefix,
                        isDirectory ? new PartialDirectorySnapshot(newChildren) : new UnknownSnapshot(newChildren)
                    );
            }

            @Override
            public ChildMap<FileSystemNode> handleDifferent(int indexOfNextBiggerChild) {
                String path = relativePath.getAsString();
                FileSystemNode newNode = snapshot.asFileSystemNode();
                diffListener.nodeAdded(newNode);
                return children.withNewChild(indexOfNextBiggerChild, path, newNode);
            }
        });
    }

    private static FileSystemNode mergeSnapshotWithNode(VfsRelativePath relativePath, MetadataSnapshot snapshot, FileSystemNode oldNode) {
        return snapshot instanceof CompleteFileSystemLocationSnapshot
            ? snapshot.asFileSystemNode()
            : oldNode.getSnapshot()
            .filter(oldSnapshot -> oldSnapshot instanceof CompleteFileSystemLocationSnapshot)
            .map(it -> oldNode)
            .orElseGet(snapshot::asFileSystemNode);
    }

    private static boolean isRegularFileOrDirectory(MetadataSnapshot metadataSnapshot) {
        return metadataSnapshot.getType() != FileType.Missing;
    }

    public static ChildMap<FileSystemNode> invalidateChild(ChildMap<FileSystemNode> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        return children.handlePath(relativePath, caseSensitivity, new ChildMap.PathRelationshipHandler<ChildMap<FileSystemNode>>() {
            @Override
            public ChildMap<FileSystemNode> handleDescendant(String childPath, int childIndex) {
                FileSystemNode oldChild = children.get(childIndex);
                Optional<FileSystemNode> invalidatedChild = oldChild.invalidate(relativePath.fromChild(childPath), caseSensitivity, diffListener);
                return invalidatedChild
                    .map(newChild -> children.withReplacedChild(childIndex, childPath, newChild))
                    .orElseGet(() -> children.withRemovedChild(childIndex));
            }

            @Override
            public ChildMap<FileSystemNode> handleAncestor(String childPath, int childIndex) {
                FileSystemNode child = children.get(childIndex);
                diffListener.nodeRemoved(child);
                return children.withRemovedChild(childIndex);
            }

            @Override
            public ChildMap<FileSystemNode> handleSame(int childIndex) {
                diffListener.nodeRemoved(children.get(childIndex));
                return children.withRemovedChild(childIndex);
            }

            @Override
            public ChildMap<FileSystemNode> handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return children;
            }

            @Override
            public ChildMap<FileSystemNode> handleDifferent(int indexOfNextBiggerChild) {
                return children;
            }
        });
    }

    public interface ChildHandler<T> {
        T handleNewChild(int insertBefore);
        T handleChildOfExisting(int childIndex);
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
