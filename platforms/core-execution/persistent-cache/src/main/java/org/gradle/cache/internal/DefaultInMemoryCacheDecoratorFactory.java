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

package org.gradle.cache.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.cache.AsyncCacheAccess;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.CrossProcessCacheAccess;
import org.gradle.cache.FileLock;
import org.gradle.cache.MultiProcessSafeIndexedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CacheDecorator} that wraps each cache with an in-memory cache that is used to short-circuit reads from the backing cache.
 * The in-memory cache is invalidated when the backing cache is changed by another process.
 *
 * Also decorates each cache so that updates to the backing cache are made asynchronously.
 */
public class DefaultInMemoryCacheDecoratorFactory implements InMemoryCacheDecoratorFactory {
    private final static Logger LOG = LoggerFactory.getLogger(DefaultInMemoryCacheDecoratorFactory.class);
    private final boolean longLivingProcess;
    private final HeapProportionalCacheSizer cacheSizer = new HeapProportionalCacheSizer();
    private final CrossBuildInMemoryCache<String, CacheDetails> caches;

    public DefaultInMemoryCacheDecoratorFactory(boolean longLivingProcess, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.longLivingProcess = longLivingProcess;
        caches = cacheFactory.newCache();
    }

    @Override
    public CacheDecorator decorator(final int maxEntriesToKeepInMemory, final boolean cacheInMemoryForShortLivedProcesses) {
        return new InMemoryCacheDecorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses);
    }

    protected <K, V> MultiProcessSafeAsyncPersistentIndexedCache<K, V> applyInMemoryCaching(String cacheId, MultiProcessSafeAsyncPersistentIndexedCache<K, V> backingCache, int maxEntriesToKeepInMemory, boolean cacheInMemoryForShortLivedProcesses) {
        if (!longLivingProcess && !cacheInMemoryForShortLivedProcesses) {
            // Short-lived process, don't cache in memory
            LOG.debug("Creating cache {} without in-memory store.", cacheId);
            return backingCache;
        }
        int targetSize = cacheSizer.scaleCacheSize(maxEntriesToKeepInMemory);
        CacheDetails cacheDetails = getCache(cacheId, targetSize);
        return new InMemoryDecoratedCache<>(backingCache, cacheDetails.entries, cacheId, cacheDetails.lockState);
    }

    private CacheDetails getCache(final String cacheId, final int maxSize) {
        CacheDetails cacheDetails = caches.get(cacheId, () -> {
            Cache<Object, Object> entries = createInMemoryCache(cacheId, maxSize);
            CacheDetails details = new CacheDetails(maxSize, entries, new AtomicReference<>());
            LOG.debug("Creating in-memory store for cache {} (max size: {})", cacheId, maxSize);
            return details;
        });
        if (cacheDetails.maxEntries != maxSize) {
            throw new IllegalStateException("Mismatched in-memory store size for cache " + cacheId + ", expected: " + maxSize + ", found: " + cacheDetails.maxEntries);
        }
        return cacheDetails;
    }

    private static Cache<Object, Object> createInMemoryCache(String cacheId, int maxSize) {
        LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize, LOG);
        final CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().maximumSize(maxSize).recordStats().removalListener(evictionListener);
        Cache<Object, Object> inMemoryCache = cacheBuilder.build();
        evictionListener.setCache(inMemoryCache);
        return inMemoryCache;
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
        public <K, V> MultiProcessSafeIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafeIndexedCache<K, V> indexedCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
            MultiProcessSafeAsyncPersistentIndexedCache<K, V> asyncCache = new AsyncCacheAccessDecoratedCache<>(asyncCacheAccess, indexedCache);
            MultiProcessSafeAsyncPersistentIndexedCache<K, V> memCache = applyInMemoryCaching(cacheId, asyncCache, maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses);
            return new CrossProcessSynchronizingIndexedCache<>(memCache, crossProcessCacheAccess);
        }
    }

    private static class CacheDetails {
        private final int maxEntries;
        private final Cache<Object, Object> entries;
        private final AtomicReference<FileLock.State> lockState;

        CacheDetails(int maxEntries, Cache<Object, Object> entries, AtomicReference<FileLock.State> lockState) {
            this.maxEntries = maxEntries;
            this.entries = entries;
            this.lockState = lockState;
        }
    }
}
