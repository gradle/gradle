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

import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * See {@link DefaultFileSystemSnapshotter} for some more details
 */
public class DefaultFileSystemMirror implements FileSystemMirror, TaskOutputsGenerationListener, RootBuildLifecycleListener {
    // Maps from interned absolute path for a file to known details for the file.
    private final Map<String, FileSnapshot> files = new ConcurrentHashMap<String, FileSnapshot>();
    private final Map<String, FileSnapshot> cacheFiles = new ConcurrentHashMap<String, FileSnapshot>();
    // Maps from interned absolute path for a directory to known details for the directory.
    private final Map<String, FileTreeSnapshot> trees = new ConcurrentHashMap<String, FileTreeSnapshot>();
    private final Map<String, FileTreeSnapshot> cacheTrees = new ConcurrentHashMap<String, FileTreeSnapshot>();
    // Maps from interned absolute path to a snapshot
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<String, Snapshot>();
    private final Map<String, Snapshot> cacheSnapshots = new ConcurrentHashMap<String, Snapshot>();
    private final FileHierarchySet cachedDirectories;

    public DefaultFileSystemMirror(List<CachedJarFileStore> fileStores) {
        FileHierarchySet cachedDirectories = DefaultFileHierarchySet.of();
        for (CachedJarFileStore fileStore : fileStores) {
            for (File file : fileStore.getFileStoreRoots()) {
                cachedDirectories = cachedDirectories.plus(file);
            }
        }
        this.cachedDirectories = cachedDirectories;
    }

    @Nullable
    @Override
    public FileSnapshot getFile(String path) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly infer that the path refers to a directory, if we have details for a descendant path (and it's not a missing file)
        if (cachedDirectories.contains(path)) {
            return cacheFiles.get(path);
        } else {
            return files.get(path);
        }
    }

    @Override
    public void putFile(FileSnapshot file) {
        if (cachedDirectories.contains(file.getPath())) {
            cacheFiles.put(file.getPath(), file);
        } else {
            files.put(file.getPath(), file);
        }
    }

    @Nullable
    @Override
    public Snapshot getContent(String path) {
        if (cachedDirectories.contains(path)) {
            return cacheSnapshots.get(path);
        } else {
            return snapshots.get(path);
        }
    }

    @Override
    public void putContent(String path, Snapshot snapshot) {
        if (cachedDirectories.contains(path)) {
            cacheSnapshots.put(path, snapshot);
        } else {
            snapshots.put(path, snapshot);
        }
    }

    @Nullable
    @Override
    public FileTreeSnapshot getDirectoryTree(String path) {
        // Could potentially also look whether we have the details for an ancestor directory tree
        // Could possibly also short-circuit some scanning if we have details for some sub trees
        if (cachedDirectories.contains(path)) {
            return cacheTrees.get(path);
        } else {
            return trees.get(path);
        }
    }

    @Override
    public void putDirectory(FileTreeSnapshot directory) {
        if (cachedDirectories.contains(directory.getPath())) {
            cacheTrees.put(directory.getPath(), directory);
        } else {
            trees.put(directory.getPath(), directory);
        }
    }

    @Override
    public void beforeTaskOutputsGenerated() {
        // When the task outputs are generated, throw away all state for files that do not live in an append-only cache.
        // This is intentionally very simple, to be improved later
        files.clear();
        trees.clear();
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
