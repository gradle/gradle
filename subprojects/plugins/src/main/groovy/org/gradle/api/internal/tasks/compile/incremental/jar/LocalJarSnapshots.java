/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.api.internal.tasks.compile.incremental.cache.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.cache.LocalJarHashesStore;

import java.io.File;
import java.util.Map;

/**
 * Contains information about the jars used for compilation.
 * It provides jar snapshots of the previous build so that incremental compilation can compare them with current jar snapshots.
 * This is required for correct handling of jar changes by the incremental java compilation.
 */
public class LocalJarSnapshots {
    private final LocalJarHashesStore localJarHashesStore;
    private final JarSnapshotCache jarSnapshotCache;

    private Map<File, JarSnapshot> snapshots;

    public LocalJarSnapshots(LocalJarHashesStore localJarHashesStore,
                             JarSnapshotCache jarSnapshotCache) {
        this.localJarHashesStore = localJarHashesStore;
        this.jarSnapshotCache = jarSnapshotCache;
    }

    public JarSnapshot getSnapshot(File jar) {
        if (snapshots == null) {
            loadSnapshots();
        }
        return snapshots.get(jar);
    }

    public void putHashes(Map<File, byte[]> newHashes) {
        //We're writing all hashes regardless of how many jars have changed.
        //This simplifies stuff and does not seem to introduce a performance hit.
        localJarHashesStore.put(newHashes);
    }

    private void loadSnapshots() {
        //We're loading all hashes regardless of how much of that is actually consumed.
        //This simplifies stuff and does not seem to introduce a performance hit.
        Map<File, byte[]> jarHashes = localJarHashesStore.get();
        snapshots = jarSnapshotCache.getJarSnapshots(jarHashes);
    }
}