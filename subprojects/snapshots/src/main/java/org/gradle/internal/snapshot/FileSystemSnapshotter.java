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

package org.gradle.internal.snapshot;

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.List;

/**
 * Provides access to snapshots of the content and metadata of the file system.
 *
 * The implementation will attempt to efficiently honour the queries,
 * maintaining some or all state in-memory and dealing with concurrent access to the same parts of the file system.
 *
 * Note: use this interface only for those files that are not expected to be changing, for example task inputs.
 */
@ThreadSafe
public interface FileSystemSnapshotter {

    /**
     * Returns the hash of the content of the file if the file is a regular file and {@code null} otherwise.
     */
    @Nullable
    HashCode getRegularFileContentHash(File file);

    /**
     * Returns the current snapshot of the contents and meta-data of the given file.
     * The file may be a regular file, a directory or missing.
     * When the specified file is a directory, details about the directory and its children are returned.
     */
    FileSystemLocationSnapshot snapshot(File file);

    /**
     * Returns snapshots of the roots of a file collection.
     */
    List<FileSystemSnapshot> snapshot(FileCollectionInternal fileCollection);
}
