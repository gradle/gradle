/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.Transformer;
import org.gradle.api.internal.cache.CrossBuildInMemoryCache;
import org.gradle.api.internal.cache.CrossBuildInMemoryCacheFactory;
import org.gradle.api.internal.cache.HeapProportionalCacheSizer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.AsyncCacheAccess;
import org.gradle.cache.internal.AsyncCacheAccessDecoratedCache;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.cache.internal.CrossProcessCacheAccess;
import org.gradle.cache.internal.CrossProcessSynchronizingCache;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafeAsyncPersistentIndexedCache;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CacheDecorator} that wraps each cache with an in-memory cache that is used to short-circuit reads from the backing cache.
 * The in-memory cache is invalidated when the backing cache is changed by another process.
 */
public class InMemoryTaskArtifactCache {
    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);
    private final boolean longLivingProcess;
    private final HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();
    private final CrossBuildInMemoryCache<String, CacheDetails> caches;

    public InMemoryTaskArtifactCache(boolean longLivingProcess, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.longLivingProcess = longLivingProcess;
        caches = cacheFactory.newCache();
    }

    public CacheDecorator decorator(final int maxEntriesToKeepInMemory, final boolean cacheInMemoryForShortLivedProcesses) {
        return new InMemoryCacheDecorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses);
    }

    private <K, V> MultiProcessSafeAsyncPersistentIndexedCache<K, V> applyInMemoryCaching(String cacheId, MultiProcessSafeAsyncPersistentIndexedCache<K, V> backingCache, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
        if (!longLivingProcess && !cacheInMemoryForShortLivedProcesses) {
            // Short lived process, don't cache in memory
            LOG.debug("Creating cache {} without in-memory store.", cacheId);
            return backingCache;
        }
        int targetSize = cacheSizer.scaleCacheSize(maxEntriesToKeepInMemory);
        CacheDetails cacheDetails = getCache(cacheId, targetSize);
        return new InMemoryDecoratedCache<K, V>(backingCache, cacheDetails.entries, cacheId, cacheDetails.lockState);
    }

    private CacheDetails getCache(final String cacheId, final int maxSize) {
        CacheDetails cacheDetails = caches.get(cacheId, new Transformer<CacheDetails, String>() {
            @Override
            public CacheDetails transform(String cacheId) {
                Cache<Object, Object> entries = createInMemoryCache(cacheId, maxSize);
                CacheDetails cacheDetails = new CacheDetails(cacheId, maxSize, entries, new AtomicReference<FileLock.State>(null));
                LOG.debug("Creating in-memory store for cache {} (max size: {})", cacheId, maxSize);
                return cacheDetails;
            }
        });
        if (cacheDetails.maxEntries != maxSize) {
            throw new IllegalStateException("Mismatched in-memory store size for cache " + cacheId + ", expected: " + maxSize + ", found: " + cacheDetails.maxEntries);
        }
        return cacheDetails;
    }

    private Cache<Object, Object> createInMemoryCache(String cacheId, int maxSize) {
        LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize);
        final CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().maximumSize(maxSize).recordStats().removalListener(evictionListener);
        Cache<Object, Object> inMemoryCache = cacheBuilder.build();
        evictionListener.setCache(inMemoryCache);
        return inMemoryCache;
    }

    public void invalidateAll() {
        caches.clear();
    }

    private class InMemoryCacheDecorator implements CacheDecorator {
        private final int maxEntriesToKeepInMemory;
        private final boolean cacheInMemoryForShortLivedProcesses;

        InMemoryCacheDecorator(int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
            this.maxEntriesToKeepInMemory = maxEntriesToKeepInMemory;
            this.cacheInMemoryForShortLivedProcesses = cacheInMemoryForShortLivedProcesses;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            InMemoryCacheDecorator other = (InMemoryCacheDecorator) obj;
            return maxEntriesToKeepInMemory == other.maxEntriesToKeepInMemory && cacheInMemoryForShortLivedProcesses == other.cacheInMemoryForShortLivedProcesses;
        }

        @Override
        public int hashCode() {
            return maxEntriesToKeepInMemory ^ (cacheInMemoryForShortLivedProcesses ? 1 : 0);
        }

        @Override
        public <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
            MultiProcessSafeAsyncPersistentIndexedCache<K, V> asyncCache = new AsyncCacheAccessDecoratedCache<K, V>(asyncCacheAccess, persistentCache);
            MultiProcessSafeAsyncPersistentIndexedCache<K, V> memCache = applyInMemoryCaching(cacheId, asyncCache, maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses);
            return new CrossProcessSynchronizingCache<K, V>(memCache, crossProcessCacheAccess);
        }
    }

    private static class CacheDetails {
        private final String cacheId;
        private final int maxEntries;
        private final Cache<Object, Object> entries;
        private final AtomicReference<FileLock.State> lockState;

        CacheDetails(String cacheId, int maxEntries, Cache<Object, Object> entries, AtomicReference<FileLock.State> lockState) {
            this.cacheId = cacheId;
            this.maxEntries = maxEntries;
            this.entries = entries;
            this.lockState = lockState;
        }
    }
}
