/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

import java.io.Closeable;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCacheLockingManager implements CacheLockingManager, Closeable {
    private final PersistentCache cache;

    public DefaultCacheLockingManager(CacheRepository cacheRepository, ArtifactCacheMetaData cacheMetaData) {
        cache = cacheRepository
                .cache(cacheMetaData.getCacheDir())
                .withCrossVersionCache(CacheBuilder.LockTarget.CacheDirectory)
                .withDisplayName("artifact cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything until we use the caches
                .open();
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public void longRunningOperation(final Runnable action) {
        cache.longRunningOperation(action);
    }

    @Override
    public <T> T useCache(Factory<? extends T> action) {
        return cache.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public <T> T longRunningOperation(Factory<? extends T> action) {
        return cache.longRunningOperation(action);
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        return cache.createCache(new PersistentIndexedCacheParameters<K, V>(cacheFileInMetaDataStore, keySerializer, valueSerializer));
    }
}
