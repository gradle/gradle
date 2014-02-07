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

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.VersionNumber;

import java.io.File;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCacheLockingManager implements CacheLockingManager {

    // If you update this, also update DefaultGradleDistribution.getArtifactCacheLayoutVersion() (which is the historical record)
    // You should also update LocallyAvailableResourceFinderFactory
    public static final VersionNumber CACHE_LAYOUT_VERSION = CacheLayout.META_DATA.getVersion();

    private final PersistentCache cache;

    public DefaultCacheLockingManager(CacheRepository cacheRepository) {
        cache = cacheRepository
                .store(CacheLayout.ROOT.getKey())
                .withCrossVersionCache()
                .withDisplayName("artifact cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything until we use the caches
                .open();
    }

    public void close() {
        cache.close();
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

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        return cache.createCache(new PersistentIndexedCacheParameters<K, V>(cacheFileInMetaDataStore, keySerializer, valueSerializer));
    }

    public File getFileStoreDirectory() {
        return createCacheRelativeDir(CacheLayout.FILE_STORE);
    }

    public File createMetaDataStore() {
        return new File(createCacheRelativeDir(CacheLayout.META_DATA), "descriptors");
    }

    private File createCacheRelativeDir(CacheLayout cacheLayout) {
        return cacheLayout.getPath(cache.getBaseDir());
    }
}
