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

import org.gradle.api.internal.Factory;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.UnitOfWorkCacheManager;

import java.io.File;

public class DefaultCacheLockingManager implements CacheLockingManager {
    public static final int CACHE_LAYOUT_VERSION = 7;
    private final UnitOfWorkCacheManager cacheManager;
    private final PersistentCache cache;

    public DefaultCacheLockingManager(FileLockManager fileLockManager, CacheRepository cacheRepository) {
        cache = cacheRepository
                .store(String.format("artifacts-%d", CACHE_LAYOUT_VERSION))
                .withDisplayName("artifact cache")
                .withVersionStrategy(CacheBuilder.VersionStrategy.SharedCache)
                .withLockMode(FileLockManager.LockMode.None) // We'll do our own
                .open();
        this.cacheManager = new UnitOfWorkCacheManager(String.format("artifact cache '%s'", getCacheDir()), getCacheDir(), fileLockManager);
    }

    public File getCacheDir() {
        return cache.getBaseDir();
    }

    public void longRunningOperation(String operationDisplayName, final Runnable action) {
        cacheManager.longRunningOperation(operationDisplayName, action);
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return cacheManager.useCache(operationDisplayName, action);
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return cacheManager.longRunningOperation(operationDisplayName, action);
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        return cacheManager.newCache(cacheFile, keyType, valueType);
    }
}
