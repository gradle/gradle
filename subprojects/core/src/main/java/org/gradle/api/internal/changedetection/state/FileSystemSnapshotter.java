/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;

import java.io.File;
import java.util.List;

/**
 * Provides access to snapshots of the content and metadata of the file system.
 *
 * The implementation will attempt to efficiently honour the queries, maintaining some or all state in-memory and dealing with concurrent access to the same parts of the file system.
 */
@ThreadSafe
public interface FileSystemSnapshotter {
    /**
     * Returns the current snapshot of the contents and meta-data of the given file. The file may be a regular file, a directory or missing. When the specified file is a directory, details about the directory itself is returned, rather than details about the children of the directory.
     */
    FileSnapshot snapshotSelf(File file);

    /**
     * Returns a simple snapshot of the contents and meta-data of the given file. The file may or may not be a regular file, a directory or missing. When the specified file is a directory, the directory and all its children are hashed.
     */
    Snapshot snapshotAll(File file);

    /**
     * Returns the current snapshot of the contents and meta-data of the given directory. The provided directory must exist and be a directory.
     */
    FileTreeSnapshot snapshotDirectoryTree(File dir);

    /**
     * Returns the current snapshot of the contents and meta-data of the given directory tree.
     */
    FileTreeSnapshot snapshotDirectoryTree(DirectoryFileTree dirTree);

    /**
     * Returns the current snapshot of the contents and meta-data of the given file tree. Note: currently does not include the root elements, if any.
     */
    List<FileSnapshot> snapshotTree(FileTreeInternal tree);
}
