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
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class LocalJarSnapshots {
    private final CacheRepository cacheRepository;
    private final JavaCompile task;
    private final JarSnapshotCache jarSnapshotCache;
    private final Hasher hasher;

    //TODO SF this cache should use File -> hash map and retrieve the snapshot from the global cache.
    //TODO SF use task-scoped standard caching

    private Map<File, JarSnapshot> snapshots;

    public LocalJarSnapshots(CacheRepository cacheRepository, JavaCompile task,
                             JarSnapshotCache jarSnapshotCache, Hasher hasher) {
        this.cacheRepository = cacheRepository;
        this.task = task;
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
        PersistentCache cacheAccess = cacheRepository.store(task, "localJarHashes")
                .withDisplayName("local jar hashes")
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();

        try {
            //TODO SF avoid hashing again, pass list of hashes only
            final Map<File, byte[]> newHashes = new HashMap<File, byte[]>();
            for (File file : newSnapshots.keySet()) {
                newHashes.put(file, hasher.hash(file));
            }

            final PersistentIndexedCache<Long, Map> cache = cacheAccess.createCache(new PersistentIndexedCacheParameters<Long, Map>("localJarHashes", Long.class, Map.class));
            cacheAccess.useCache("loading local jar hashes", new Runnable() {
                public void run() {
                    cache.put(0L, newHashes);
                }
            });
        } finally {
            cacheAccess.close();
        }
    }

    private void loadSnapshots() {
        PersistentCache cacheAccess = cacheRepository.store(task, "localJarHashes")
                .withDisplayName("local jar hashes")
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();
        try {
            final PersistentIndexedCache<Long, Map> cache = cacheAccess.createCache(new PersistentIndexedCacheParameters<Long, Map>("localJarHashes", Long.class, Map.class));
            Map<File, byte[]> jarHashes = cacheAccess.useCache("loading local jar hashes", new Factory<Map>() {
                public Map create() {
                    return cache.get(0L);
                }
            });
            snapshots = jarSnapshotCache.getJarSnapshots(jarHashes);
        } finally {
            cacheAccess.close();
        }
    }
}