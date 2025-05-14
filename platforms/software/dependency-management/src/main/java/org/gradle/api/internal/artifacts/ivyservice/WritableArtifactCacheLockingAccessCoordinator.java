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

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.filestore.DefaultArtifactIdentifierFileStore;
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.resource.cached.DefaultExternalResourceFileStore;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.TimestampSuppliers;
import org.gradle.internal.versionedcache.UnusedVersionsCacheCleanup;
import org.gradle.internal.versionedcache.UsedGradleVersions;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.function.Function;
import java.util.function.Supplier;

public class WritableArtifactCacheLockingAccessCoordinator implements ArtifactCacheLockingAccessCoordinator, Closeable {
    private final PersistentCache cache;

    public WritableArtifactCacheLockingAccessCoordinator(
            UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
            ArtifactCacheMetadata cacheMetaData,
            FileAccessTimeJournal fileAccessTimeJournal,
            UsedGradleVersions usedGradleVersions,
            CacheConfigurationsInternal cacheConfigurations,
            CacheCleanupStrategyFactory cacheCleanupStrategyFactory) {
        cache = unscopedCacheBuilderFactory
                .cache(cacheMetaData.getCacheDir())
                .withDisplayName("artifact cache")
                .withInitialLockMode(FileLockManager.LockMode.OnDemand) // Don't need to lock anything until we use the caches
                .withCleanupStrategy(cacheCleanupStrategyFactory.create(createCleanupAction(cacheMetaData, fileAccessTimeJournal, usedGradleVersions, cacheConfigurations), cacheConfigurations.getCleanupFrequency()::get))
                .open();
    }

    private CleanupAction createCleanupAction(ArtifactCacheMetadata cacheMetaData, FileAccessTimeJournal fileAccessTimeJournal, UsedGradleVersions usedGradleVersions, CacheConfigurationsInternal cacheConfigurations) {
        return CompositeCleanupAction.builder()
                .add(UnusedVersionsCacheCleanup.create(CacheLayout.MODULES.getName(), CacheLayout.MODULES.getVersionMapping(), usedGradleVersions))
                .add(cacheMetaData.getExternalResourcesStoreDirectory(),
                    UnusedVersionsCacheCleanup.create(CacheLayout.RESOURCES.getName(), CacheLayout.RESOURCES.getVersionMapping(), usedGradleVersions),
                    new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(DefaultExternalResourceFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, getMaxAgeTimestamp(cacheConfigurations)))
                .add(cacheMetaData.getFileStoreDirectory(),
                    UnusedVersionsCacheCleanup.create(CacheLayout.FILE_STORE.getName(), CacheLayout.FILE_STORE.getVersionMapping(), usedGradleVersions),
                    new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(DefaultArtifactIdentifierFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, getMaxAgeTimestamp(cacheConfigurations)))
                .add(cacheMetaData.getMetaDataStoreDirectory().getParentFile(),
                    UnusedVersionsCacheCleanup.create(CacheLayout.META_DATA.getName(), CacheLayout.META_DATA.getVersionMapping(), usedGradleVersions))
                // Cleanup old unused 'transforms-X' directories too. Transforms are now cached in 'caches/<gradle-version>/transforms'.
                .add(UnusedVersionsCacheCleanup.create(CacheLayout.TRANSFORMS.getName(), CacheLayout.TRANSFORMS.getVersionMapping(), usedGradleVersions))
                .build();
    }

    private Supplier<Long> getMaxAgeTimestamp(CacheConfigurationsInternal cacheConfigurations) {
        Integer maxAgeProperty = Integer.getInteger("org.gradle.internal.cleanup.external.max.age");
        if (maxAgeProperty == null) {
            return cacheConfigurations.getDownloadedResources().getEntryRetentionTimestampSupplier();
        } else {
            return TimestampSuppliers.daysAgo(maxAgeProperty);
        }
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public <T> T withFileLock(Supplier<? extends T> action) {
        return cache.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cache.withFileLock(action);
    }

    @Override
    public <T> T useCache(Supplier<? extends T> action) {
        return cache.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public <K, V> IndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        final IndexedCache<K, V> indexedCache = cache.createIndexedCache(IndexedCacheParameters.of(cacheFileInMetaDataStore, keySerializer, valueSerializer));
        return new CacheLockingIndexedCache<>(indexedCache);
    }

    private class CacheLockingIndexedCache<K, V> implements IndexedCache<K, V> {
        private final IndexedCache<K, V> indexedCache;

        public CacheLockingIndexedCache(IndexedCache<K, V> indexedCache) {
            this.indexedCache = indexedCache;
        }

        @Nullable
        @Override
        public V getIfPresent(final K key) {
            return cache.useCache(() -> indexedCache.getIfPresent(key));
        }

        @Override
        public V get(final K key, final Function<? super K, ? extends V> producer) {
            return cache.useCache(() -> indexedCache.get(key, producer));
        }

        @Override
        public void put(final K key, final V value) {
            cache.useCache(() -> indexedCache.put(key, value));
        }

        @Override
        public void remove(final K key) {
            cache.useCache(() -> indexedCache.remove(key));
        }
    }
}
