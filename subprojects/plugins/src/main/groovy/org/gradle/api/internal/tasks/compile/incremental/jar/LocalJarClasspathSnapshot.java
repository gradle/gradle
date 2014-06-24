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
import org.gradle.api.internal.tasks.compile.incremental.cache.LocalJarClasspathSnapshotStore;

import java.io.File;
import java.util.Map;

/**
 * Contains information about the jars used for compilation.
 * It provides jar snapshots of the previous build so that incremental compilation can compare them with current jar snapshots.
 * This is required for correct handling of jar changes by the incremental java compilation.
 */
public class LocalJarClasspathSnapshot {
    private final LocalJarClasspathSnapshotStore localJarClasspathSnapshotStore;
    private final JarSnapshotCache jarSnapshotCache;

    private Map<File, JarSnapshot> snapshots;

    public LocalJarClasspathSnapshot(LocalJarClasspathSnapshotStore localJarClasspathSnapshotStore,
                                     JarSnapshotCache jarSnapshotCache) {
        this.localJarClasspathSnapshotStore = localJarClasspathSnapshotStore;
        this.jarSnapshotCache = jarSnapshotCache;
    }

    public JarSnapshot getSnapshot(File jar) {
        if (snapshots == null) {
            //there may be not jar changes at all so we load the snapshots lazily
            loadSnapshots();
        }
        return snapshots.get(jar);
    }

    public void putClasspathSnapshot(JarClasspathSnapshotData data) {
        //We're writing all hashes regardless of how many jars have updated/changed.
        //This simplifies stuff and does not seem to introduce a performance hit.
        localJarClasspathSnapshotStore.put(data);
    }

    private void loadSnapshots() {
        //We're loading all hashes regardless of how much of that is actually consumed.
        //This simplifies stuff and does not seem to introduce a performance hit.
        JarClasspathSnapshotData data = localJarClasspathSnapshotStore.get();
        snapshots = jarSnapshotCache.getJarSnapshots(data.getJarHashes());
    }
}