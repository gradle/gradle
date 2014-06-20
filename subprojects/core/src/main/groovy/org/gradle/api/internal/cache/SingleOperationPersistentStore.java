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

package org.gradle.api.internal.cache;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;

import static org.apache.commons.lang.WordUtils.uncapitalize;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.util.GUtil.toCamelCase;

public class SingleOperationPersistentStore<V> {

    private final PersistentIndexedCache<Long, V> cache;
    private final PersistentCache cacheAccess;
    private final String cacheName;

    //The cache only keeps single value, so we're always use the same index.
    //We probably should improve our cross-process caching infrastructure so that we support Stores (e.g. not-indexed caches).
    private final static long CACHE_KEY = 0;

    public SingleOperationPersistentStore(CacheRepository cacheRepository, Object scope, String cacheName, Class<V> valueClass) {
        this.cacheName = cacheName;
        String identifier = uncapitalize(toCamelCase(cacheName));
        cacheAccess = cacheRepository.store(scope, identifier)
                .withDisplayName(cacheName + " cache")
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();

        cache = cacheAccess.createCache(new PersistentIndexedCacheParameters<Long, V>("localJarHashes", Long.class, valueClass));
    }

    public void putAndClose(final V value) {
        //TODO SF if the cacheAccess is opened with exclusive lock, do I still have to use 'useCache'? Applies to the getAndClose() method, too
        try {
            cacheAccess.useCache("storing " + cacheName, new Runnable() {
                public void run() {
                    cache.put(CACHE_KEY, value);
                }
            });
        } finally {
            cacheAccess.close();
        }
    }

    public V getAndClose() {
        try {
            return cacheAccess.useCache("storing " + cacheName, new Factory<V>() {
                public V create() {
                    return cache.get(CACHE_KEY);
                }
            });
        } finally {
            cacheAccess.close();
        }
    }
}
