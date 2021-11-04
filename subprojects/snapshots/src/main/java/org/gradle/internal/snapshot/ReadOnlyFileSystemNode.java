/*
 * Copyright 2020 the original author or authors.
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

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

public interface ReadOnlyFileSystemNode {
    ReadOnlyFileSystemNode EMPTY = new ReadOnlyFileSystemNode() {
        @Override
        public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
            return Optional.empty();
        }

        @Override
        public boolean hasDescendants() {
            return false;
        }

        @Override
        public ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
            return EMPTY;
        }

        @Override
        public Optional<MetadataSnapshot> getSnapshot() {
            return Optional.empty();
        }

        @Override
        public Stream<FileSystemLocationSnapshot> rootSnapshots() {
            return Stream.empty();
        }
    };

    /**
     * Gets a snapshot from the current node with relative path filePath.substring(offset).
     *
     * When calling this method, the caller needs to make sure the the snapshot is a child of this node.
     */
    Optional<MetadataSnapshot> getSnapshot(VfsRelativePath relativePath, CaseSensitivity caseSensitivity);

    boolean hasDescendants();

    ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity);

    /**
     * The snapshot information at this node.
     *
     * {@link Optional#empty()} if no information is available.
     */
    Optional<MetadataSnapshot> getSnapshot();

    /**
     * Returns all the snapshot roots accessible from the node.
     */
    Stream<FileSystemLocationSnapshot> rootSnapshots();

    interface NodeVisitor {
        void visitNode(FileSystemNode node, @Nullable FileSystemNode parent);
    }
}
