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

package org.gradle.internal.fingerprint;

import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.internal.snapshot.FileSystemSnapshot;

/**
 * A snapshotter for generic file trees, which are not based on a directory on disk.
 *
 * Examples of a generic file tree is a {@link org.gradle.api.internal.file.archive.TarFileTree} backed by a non-file resource.
 * This is needed to build a Merkle directory tree from the elements of a file tree obtained by {@link org.gradle.api.file.FileTree#visit(FileVisitor)}.
 */
public interface GenericFileTreeSnapshotter {
    FileSystemSnapshot snapshotFileTree(FileTreeInternal tree);
}
