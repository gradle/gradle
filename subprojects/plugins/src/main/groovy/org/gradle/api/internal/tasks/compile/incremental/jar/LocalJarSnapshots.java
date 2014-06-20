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

import org.gradle.api.internal.hash.Hasher;
import org.gradle.api.internal.tasks.compile.incremental.cache.JarSnapshotCache;
import org.gradle.api.internal.tasks.compile.incremental.cache.LocalJarHashesStore;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocalJarSnapshots {
    private final LocalJarHashesStore localJarHashesStore;
    private final JarSnapshotCache jarSnapshotCache;
    private final Hasher hasher;

    private Map<File, JarSnapshot> snapshots;

    public LocalJarSnapshots(LocalJarHashesStore localJarHashesStore,
                             JarSnapshotCache jarSnapshotCache, Hasher hasher) {
        this.localJarHashesStore = localJarHashesStore;
        this.jarSnapshotCache = jarSnapshotCache;
        this.hasher = hasher;
    }

    public JarSnapshot getSnapshot(File jar) {
        if (snapshots == null) {
            loadSnapshots();
        }
        return snapshots.get(jar);
    }

    public void putSnapshots(Map<File, JarSnapshot> newSnapshots) {
        //TODO SF avoid hashing again, pass list of hashes only
        final Map<File, byte[]> newHashes = new HashMap<File, byte[]>();
        for (File file : newSnapshots.keySet()) {
            newHashes.put(file, hasher.hash(file));
        }

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