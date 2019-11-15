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

/**
 * Any snapshot in the tree of the virtual file system.
 */
public interface FileSystemNode {

    /**
     * Gets a snapshot from the current node with relative path filePath.substring(offset).
     *
     * When calling this method, the caller needs to make sure the the snapshot is a child of this node.
     * Must not include the {@link #getPathToParent()}..
     */
    Optional<MetadataSnapshot> getSnapshot(String absolutePath, int offset);

    Optional<MetadataSnapshot> getSnapshot();

    /**
     * Stores information to the virtual file system that we have learned about.
     *
     * Complete information, like {@link CompleteFileSystemLocationSnapshot}s, are not touched nor replaced.
     * @param absolutePath the path to update, starting from offset. Must not include the {@link #getPathToParent()}.
     */
    FileSystemNode store(String absolutePath, int offset, MetadataSnapshot snapshot);

    /**
     * Invalidates part of the node.
     *
     * @param absolutePath the path to invalidate, starting from the offset. Must not include the {@link #getPathToParent()}.
     */
    Optional<FileSystemNode> invalidate(String absolutePath, int offset);

    /**
     * The path to the parent snapshot or the root of the file system.
     */
    String getPathToParent();

    /**
     * Creates a new node with the same children, but a different path to the parent.
     */
    FileSystemNode withPathToParent(String newPathToParent);
}
