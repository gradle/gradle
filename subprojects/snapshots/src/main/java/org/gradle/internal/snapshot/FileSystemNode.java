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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface FileSystemNode {

    /**
     * Gets a snapshot from the current node with relative path filePath.substring(offset).
     *
     * When calling this method, the caller needs to make sure the the snapshot is a child of this node or this node.
     * That means that filePath.substring(offset) does not include the {@link #getPrefix()}.
     * Therefore, when filePath.length < offset, then this node will be returned.
     */
    Optional<MetadataSnapshot> getSnapshot(String filePath, int offset);

    /**
     * Adds more information to the file system node.
     *
     * Complete information, like {@link FileSystemLocationSnapshot}s, are not touched nor replaced.
     * @param path the path to update. Must not include the {@link #getPrefix()}.
     */
    FileSystemNode update(String path, MetadataSnapshot snapshot);

    /**
     * Invalidates part of the node.
     *
     * @param path the path to invalidate. Must not include the {@link #getPrefix()}.
     */
    Optional<FileSystemNode> invalidate(String path);

    String getPrefix();

    /**
     * Only used for testing, should maybe removed
     */
    void collect(int depth, List<String> prefixes);

    /**
     * Creates a new node with the same children, but a different prefix.
     */
    FileSystemNode withPrefix(String newPrefix);

    static Optional<MetadataSnapshot> thisOrGet(MetadataSnapshot current, String filePath, int offset, Supplier<Optional<MetadataSnapshot>> supplier) {
        if (filePath.length() + 1 == offset) {
            return Optional.of(current);
        }
        return supplier.get();
    }
}
