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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.tasks.execution.TaskOutputChangesListener;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.file.FileMetadataSnapshot;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * See {@link DefaultFileSystemSnapshotter} for some more details
 */
public class DefaultFileSystemMirror implements FileSystemMirror, TaskOutputChangesListener, RootBuildLifecycleListener {
    // Maps from interned absolute path for a file to metadata for the file.
    private final Map<String, FileMetadataSnapshot> metadata = new ConcurrentHashMap<String, FileMetadataSnapshot>();
    private final Map<String, FileMetadataSnapshot> cacheMetadata = new ConcurrentHashMap<String, FileMetadataSnapshot>();
    // Maps from interned absolute path for a file to snapshot for the file.
    private final Map<String, PhysicalSnapshot> files = new ConcurrentHashMap<String, PhysicalSnapshot>();
    private final Map<String, PhysicalSnapshot> cacheFiles = new ConcurrentHashMap<String, PhysicalSnapshot>();

    private final WellKnownFileLocations wellKnownFileLocations;

    public DefaultFileSystemMirror(WellKnownFileLocations wellKnownFileLocations) {
        this.wellKnownFileLocations = wellKnownFileLocations;
    }

    @Nullable
    @Override
    public PhysicalSnapshot getSnapshot(String absolutePath) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly infer that the path refers to a directory, if we have details for a descendant path (and it's not a missing file)
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            return cacheFiles.get(absolutePath);
        }
        return files.get(absolutePath);
    }

    @Override
    public void putSnapshot(PhysicalSnapshot file) {
        if (wellKnownFileLocations.isImmutable(file.getAbsolutePath())) {
            cacheFiles.put(file.getAbsolutePath(), file);
        } else {
            files.put(file.getAbsolutePath(), file);
        }
    }

    @Override
    public FileMetadataSnapshot getMetadata(String absolutePath) {
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            return cacheMetadata.get(absolutePath);
        }
        return metadata.get(absolutePath);
    }

    @Override
    public void putMetadata(String absolutePath, FileMetadataSnapshot metadata) {
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            cacheMetadata.put(absolutePath, metadata);
        } else {
            this.metadata.put(absolutePath, metadata);
        }
    }

    @Override
    public void beforeTaskOutputChanged() {
        // When the task outputs are generated, throw away all state for files that do not live in an append-only cache.
        // This is intentionally very simple, to be improved later
        metadata.clear();
        files.clear();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        // We throw away all state between builds
        metadata.clear();
        cacheMetadata.clear();
        files.clear();
        cacheFiles.clear();
    }
}
