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

import java.util.Optional;
import java.util.function.Supplier;

public class SnapshotUtil {

    public static <T extends FileSystemNode> Optional<MetadataSnapshot> getMetadataFromChildren(ChildMap<T> children, VfsRelativePath relativePath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        return children.findChild(relativePath, caseSensitivity, new ChildMap.FindChildHandler<T, Optional<MetadataSnapshot>>() {
            @Override
            public Optional<MetadataSnapshot> handleDescendant(String childPath, T child) {
                return child.getSnapshot(relativePath.fromChild(childPath), caseSensitivity);
            }

            @Override
            public Optional<MetadataSnapshot> handleNotFound() {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleSame(T child) {
                return child.getSnapshot();
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
                // TODO: This is not correct, it should be a node with the child at relativePath.fromChild(childPath).
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
}
