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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.api.internal.filestore.UniquePathKeyFileStore;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.PersistentIndexedCacheParameters;
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
                .withDisplayName("artifact cache")
                .withVersionStrategy(CacheBuilder.VersionStrategy.SharedCache)
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything until we use the caches
                .open();

        initMetaDataStoreDir();
    }

    private void initMetaDataStoreDir() {
        File metaDataStoreDir = getMetaDataStoreDir();

        if(!metaDataStoreDir.exists()) {
            if(!metaDataStoreDir.mkdirs()) {
                throw new UncheckedIOException(String.format("Unable to create directory '%s'", metaDataStoreDir.getName()));
            }
        }
    }

    private File getMetaDataStoreDir() {
        return CacheLayout.META_DATA.getPath(cache.getBaseDir());
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

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        File cacheFileInMetaDataStore = new File(getMetaDataStoreDir(), cacheFile);
        return cache.createCache(new PersistentIndexedCacheParameters<K, V>(cacheFileInMetaDataStore, keySerializer, valueSerializer));
    }

    public PathKeyFileStore createFileStore() {
        return createCacheRelativeStore(CacheLayout.FILE_STORE);
    }

    public PathKeyFileStore createMetaDataStore() {
        return createCacheRelativeStore(CacheLayout.META_DATA, "descriptors");
    }

    private PathKeyFileStore createCacheRelativeStore(CacheLayout cacheLayout) {
        return new UniquePathKeyFileStore(createCacheRelativeDir(cacheLayout));
    }

    private PathKeyFileStore createCacheRelativeStore(CacheLayout cacheLayout, String appendedPath) {
        return new UniquePathKeyFileStore(new File(createCacheRelativeDir(cacheLayout), appendedPath));
    }

    private File createCacheRelativeDir(CacheLayout cacheLayout) {
        return cacheLayout.getPath(cache.getBaseDir());
    }
}
