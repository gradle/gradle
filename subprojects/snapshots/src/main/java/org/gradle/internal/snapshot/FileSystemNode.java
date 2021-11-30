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

import javax.annotation.CheckReturnValue;
import java.util.Optional;

/**
 * Any snapshot in the tree of the virtual file system.
 */
public interface FileSystemNode extends ReadOnlyFileSystemNode {

    /**
     * Stores information to the virtual file system that we have learned about.
     *
     * Complete information, like {@link FileSystemLocationSnapshot}s, are not touched nor replaced.
     */
    @CheckReturnValue
    FileSystemNode store(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, MetadataSnapshot snapshot, SnapshotHierarchy.NodeDiffListener diffListener);

    /**
     * Invalidates part of the node.
     */
    @CheckReturnValue
    Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener);
}
