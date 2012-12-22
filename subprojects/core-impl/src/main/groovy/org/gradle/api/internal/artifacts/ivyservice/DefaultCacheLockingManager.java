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
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;

public class DefaultCacheLockingManager implements CacheLockingManager {
    public static final int CACHE_LAYOUT_VERSION = 23;
    private final PersistentCache cache;

    public DefaultCacheLockingManager(CacheRepository cacheRepository) {
        cache = cacheRepository
                .store(String.format("artifacts-%d", CACHE_LAYOUT_VERSION))
                .withDisplayName("artifact cache")
                .withVersionStrategy(CacheBuilder.VersionStrategy.SharedCache)
                .withLockMode(FileLockManager.LockMode.None) // Don't need to lock anything until we use the caches
                .open();
    }

    public File getCacheDir() {
        return cache.getBaseDir();
    }

    public void longRunningOperation(String operationDisplayName, final Runnable action) {
        cache.longRunningOperation(operationDisplayName, action);
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return cache.useCache(operationDisplayName, action);
    }

    public void useCache(String operationDisplayName, Runnable action) {
        cache.useCache(operationDisplayName, action);
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return cache.longRunningOperation(operationDisplayName, action);
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        return cache.createCache(cacheFile, keyType, valueType);
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return cache.createCache(cacheFile, keySerializer, valueSerializer);
    }
}
