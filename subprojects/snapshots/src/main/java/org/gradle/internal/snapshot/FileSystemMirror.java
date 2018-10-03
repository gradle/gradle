/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.file.FileMetadataSnapshot;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Maintains an in-memory mirror of the state of the filesystem.
 *
 * This is intended to only be used by {@link org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter}. Use {@link FileSystemSnapshotter} instead.
 */
@ThreadSafe
public interface FileSystemMirror {
    @Nullable
    FileSystemLocationSnapshot getSnapshot(String absolutePath);

    void putSnapshot(FileSystemLocationSnapshot file);

    @Nullable
    FileMetadataSnapshot getMetadata(String absolutePath);

    void putMetadata(String absolutePath, FileMetadataSnapshot stat);
}
