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

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * See {@link DefaultFileSystemSnapshotter} for some more details
 */
public class DefaultFileSystemMirror implements FileSystemMirror, TaskOutputChangesListener, RootBuildLifecycleListener {
    // Maps from interned absolute path for a file to known details for the file.
    private final Map<String, PhysicalSnapshot> files = new ConcurrentHashMap<String, PhysicalSnapshot>();
    private final Map<String, PhysicalSnapshot> cacheFiles = new ConcurrentHashMap<String, PhysicalSnapshot>();
    // Maps from interned absolute path for a directory to known details for the directory.
    private final Map<String, PhysicalSnapshot> trees = new ConcurrentHashMap<String, PhysicalSnapshot>();
    private final Map<String, PhysicalSnapshot> cacheTrees = new ConcurrentHashMap<String, PhysicalSnapshot>();
    // Maps from interned absolute path to a snapshot
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<String, Snapshot>();
    private final Map<String, Snapshot> cacheSnapshots = new ConcurrentHashMap<String, Snapshot>();
    private final WellKnownFileLocations wellKnownFileLocations;

    public DefaultFileSystemMirror(WellKnownFileLocations wellKnownFileLocations) {
        this.wellKnownFileLocations = wellKnownFileLocations;
    }

    @Nullable
    @Override
    public PhysicalSnapshot getFile(String absolutePath) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly infer that the path refers to a directory, if we have details for a descendant path (and it's not a missing file)
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            return cacheFiles.get(absolutePath);
        }
        return files.get(absolutePath);
    }

    @Override
    public void putFile(PhysicalSnapshot file) {
        if (wellKnownFileLocations.isImmutable(file.getAbsolutePath())) {
            cacheFiles.put(file.getAbsolutePath(), file);
        } else {
            files.put(file.getAbsolutePath(), file);
        }
    }

    @Nullable
    @Override
    public Snapshot getContent(String absolutePath) {
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            return cacheSnapshots.get(absolutePath);
        } else {
            return snapshots.get(absolutePath);
        }
    }

    @Override
    public void putContent(String absolutePath, Snapshot snapshot) {
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            cacheSnapshots.put(absolutePath, snapshot);
        } else {
            snapshots.put(absolutePath, snapshot);
        }
    }

    @Nullable
    @Override
    public PhysicalSnapshot getDirectoryTree(String absolutePath) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly also short-circuit some scanning if we have details for some sub trees
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            return cacheTrees.get(absolutePath);
        } else {
            return trees.get(absolutePath);
        }
    }

    @Override
    public void putDirectory(String absolutePath, PhysicalSnapshot directory) {
        if (wellKnownFileLocations.isImmutable(absolutePath)) {
            cacheTrees.put(absolutePath, directory);
        } else {
            trees.put(absolutePath, directory);
        }
    }

    @Override
    public void beforeTaskOutputChanged() {
        // When the task outputs are generated, throw away all state for files that do not live in an append-only cache.
        // This is intentionally very simple, to be improved later
        trees.clear();
        files.clear();
        snapshots.clear();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        // We throw away all state between builds
        files.clear();
        cacheFiles.clear();
        trees.clear();
        cacheTrees.clear();
        snapshots.clear();
        cacheSnapshots.clear();
    }
}
