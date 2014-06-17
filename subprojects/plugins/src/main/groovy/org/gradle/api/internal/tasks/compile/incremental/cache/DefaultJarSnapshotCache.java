/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshot;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultJarSnapshotCache implements JarSnapshotCache {

    private final CacheRepository cacheRepository;
    private PersistentCache cache;
    private final Object lock = new Object();
    private PersistentIndexedCache<byte[], JarSnapshot> theCache;

    public DefaultJarSnapshotCache(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    public JarSnapshot loadSnapshot(final byte[] hash) {
        synchronized (lock) {
            if (cache == null) {
                cache = cacheRepository
                        .cache("jarSnapshots")
                        .withDisplayName("jar snapshots cache")
                        .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                        .open();
            }
            PersistentIndexedCacheParameters<byte[], JarSnapshot> params =
                    new PersistentIndexedCacheParameters<byte[], JarSnapshot>("jarSnapshots", byte[].class, JarSnapshot.class);
            theCache = cache.createCache(params);
        }

        return cache.useCache("Loading jar snapshot", new Factory<JarSnapshot>() {
            public JarSnapshot create() {
                return theCache.get(hash);
            }
        });
    }

    public void storeSnapshot(final byte[] jarHash, final JarSnapshot snapshot) {
        cache.useCache("Loading jar snapshot", new Runnable() {
            public void run() {
                theCache.put(jarHash, snapshot);
            }
        });
    }
}