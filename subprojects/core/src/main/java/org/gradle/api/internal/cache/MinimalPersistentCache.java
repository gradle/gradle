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

import org.gradle.cache.*;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.serialize.Serializer;

import static org.apache.commons.lang.WordUtils.uncapitalize;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.util.GUtil.toCamelCase;

/**
 * Very simple cache implementation that uses Gradle's standard persistence cache mechanism.
 * Provides synchronisation when get() method is used.
 * Locking is extremely fine-grained, every load operation is synchronized, every store operation is synchronized.
 * Useful as a starting point, before profiler shows that locking needs to be more coarse grained.
 */
public class MinimalPersistentCache<K, V> implements Cache<K, V>, Stoppable {

    private final PersistentCache cacheAccess;
    private final PersistentIndexedCache<K, V> cache;
    private final String cacheName;

    public MinimalPersistentCache(CacheRepository cacheRepository, String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheName = cacheName;
        String identifier = uncapitalize(toCamelCase(cacheName));
        cacheAccess = cacheRepository
                .cache(identifier)
                .withDisplayName(cacheName + " cache")
                .withLockOptions(mode(FileLockManager.LockMode.None))
                .open();

        PersistentIndexedCacheParameters<K, V> params =
                new PersistentIndexedCacheParameters<K, V>(identifier, keySerializer, valueSerializer);
        cache = cacheAccess.createCache(params);
    }

    //TODO SF if this refactoring makes sense, unit-test
    public V get(final K key, Factory<V> factory) {
        V cached = cacheAccess.useCache("Loading " + cacheName, new Factory<V>() {
            public V create() {
                return cache.get(key);
            }
        });
        if (cached != null) {
            return cached;
        }

        final V value = factory.create(); //don't synchronize value creation
        //we could potentially avoid creating value that is already being created by a different thread.

        cacheAccess.useCache("Storing " + cacheName, new Runnable() {
            public void run() {
                cache.put(key, value);
            }
        });
        return value;
    }

    public CacheAccess getCacheAccess() {
        return cacheAccess;
    }

    public PersistentIndexedCache<K, V> getCache() {
        return cache;
    }

    public void stop() {
        cacheAccess.close();
    }
}
