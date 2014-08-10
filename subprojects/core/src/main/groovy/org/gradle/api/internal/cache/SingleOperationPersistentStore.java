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
import org.gradle.messaging.serialize.Serializer;

import static org.apache.commons.lang.WordUtils.uncapitalize;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.messaging.serialize.BaseSerializerFactory.LONG_SERIALIZER;
import static org.gradle.util.GUtil.toCamelCase;

public class SingleOperationPersistentStore<V> {

    //The cache only keeps single value, so we're always use the same index.
    //We probably should improve our cross-process caching infrastructure so that we support Stores (e.g. not-indexed caches).
    private final static long CACHE_KEY = 0;

    private final CacheRepository cacheRepository;

    private final Object scope;
    private final String cacheName;
    private final Serializer<V> valueSerializer;

    private PersistentIndexedCache<Long, V> cache;
    private PersistentCache cacheAccess;

    public SingleOperationPersistentStore(CacheRepository cacheRepository, Object scope, String cacheName, Serializer<V> valueSerializer) {
        this.cacheRepository = cacheRepository;
        this.scope = scope;
        this.cacheName = cacheName;
        this.valueSerializer = valueSerializer;
    }

    //Opens and closes the cache for operation
    public void putAndClose(final V value) {
        initCaches("write");
        try {
            cache.put(CACHE_KEY, value);
        } finally {
            closeCaches();
        }
    }

    //Opens and closes the cache for operation
    public V getAndClose() {
        initCaches("read");
        try {
            return cache.get(CACHE_KEY);
        } finally {
            cacheAccess.close();
        }
    }

    private void initCaches(String operation) {
        String identifier = uncapitalize(toCamelCase(cacheName));
        cacheAccess = cacheRepository.store(scope, identifier)
                .withDisplayName(cacheName + " " + operation + " cache")
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();

        cache = cacheAccess.createCache(new PersistentIndexedCacheParameters<Long, V>(identifier, LONG_SERIALIZER, valueSerializer));
    }

    private void closeCaches() {
        cacheAccess.close();
        cache = null;
        cacheAccess = null;
    }
}
