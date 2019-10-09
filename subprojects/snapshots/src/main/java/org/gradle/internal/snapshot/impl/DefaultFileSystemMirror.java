/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemMirror;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * See {@link DefaultFileSystemSnapshotter} for some more details
 */
public class DefaultFileSystemMirror implements FileSystemMirror {
    // Maps from interned absolute path for a file to metadata for the file.
    private final Map<String, FileMetadataSnapshot> metadata = new ConcurrentHashMap<>();
    // Maps from interned absolute path for a file to snapshot for the file.
    private final Map<String, FileSystemLocationSnapshot> files = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public FileSystemLocationSnapshot getSnapshot(String absolutePath) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly infer that the path refers to a directory, if we have details for a descendant path (and it's not a missing file)
        return files.get(absolutePath);
    }

    @Override
    public void putSnapshot(FileSystemLocationSnapshot snapshot) {
        files.put(snapshot.getAbsolutePath(), snapshot);
    }

    @Override
    public FileMetadataSnapshot getMetadata(String absolutePath) {
        return metadata.get(absolutePath);
    }

    @Override
    public void putMetadata(String absolutePath, FileMetadataSnapshot metadata) {
        this.metadata.put(absolutePath, metadata);
    }

    public void invalidateAll() {
        // When the outputs are generated, throw away all state.
        // This is intentionally very simple, to be improved later
        metadata.clear();
        files.clear();
    }

    public void invalidate(Iterable<String> affectedOutputPaths) {
        for (String affectedOutputPath : affectedOutputPaths) {
            metadata.remove(affectedOutputPath);
            files.remove(affectedOutputPath);
        }
    }
}
