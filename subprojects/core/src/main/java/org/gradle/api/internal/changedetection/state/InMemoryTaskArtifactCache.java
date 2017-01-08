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
import org.gradle.initialization.SessionLifecycleListener;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CacheDecorator} that wraps each cache with an in-memory cache that is used to short-circuit reads from the backing cache.
 * The in-memory cache is invalidated when the backing cache is changed by another process.
 */
public class InMemoryTaskArtifactCache implements SessionLifecycleListener {
    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);
    private final boolean longLivingProcess;
    private final Object lock = new Object();
    // Retain a strong reference to the caches for this session and the most recent previous session. Retain soft references to everything else
    private final Set<CacheDetails> cachesForThisSession;
    private final Set<CacheDetails> cachesForPreviousSession;
    private final Map<String, SoftReference<CacheDetails>> allCaches;

    public InMemoryTaskArtifactCache(boolean longLivingProcess) {
        this.longLivingProcess = longLivingProcess;
        cachesForThisSession = new HashSet<CacheDetails>();
        cachesForPreviousSession = new HashSet<CacheDetails>();
        allCaches = new HashMap<String, SoftReference<CacheDetails>>();
    }

    @Override
    public void afterStart() {
    }

    @Override
    public void beforeComplete() {
        synchronized (lock) {
            if (LOG.isDebugEnabled()) {
                for (CacheDetails cacheDetails : cachesForThisSession) {
                    LOG.debug("Retaining cache {} for next session (size: {}, max-size: {})", cacheDetails.cacheId, cacheDetails.entries.size(), cacheDetails.maxEntries);
                }
            }
            // Retain the caches created for this session
            cachesForPreviousSession.clear();
            cachesForPreviousSession.addAll(cachesForThisSession);
            cachesForThisSession.clear();
        }
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
        int targetSize = new HeapProportionalCacheSizer().scaleCacheSize(maxEntriesToKeepInMemory);
        CacheDetails cacheDetails = getCache(cacheId, targetSize);
        return new InMemoryDecoratedCache<K, V>(backingCache, cacheDetails.entries, cacheId, cacheDetails.lockState);
    }

    private CacheDetails getCache(String cacheId, int maxSize) {
        synchronized (lock) {
            SoftReference<CacheDetails> reference = allCaches.get(cacheId);
            if (reference != null) {
                CacheDetails cacheDetails = reference.get();
                if (cacheDetails != null && cacheDetails.maxEntries >= maxSize) {
                    // Retain a strong reference to details for this session
                    LOG.debug("Reusing in-memory store for cache {} (size: {}, max size: {})", cacheId, cacheDetails.entries.size(), maxSize);
                    cachesForThisSession.add(cacheDetails);
                    return cacheDetails;
                }
            }
            Cache<Object, Object> entries = createInMemoryCache(cacheId, maxSize);
            CacheDetails cacheDetails = new CacheDetails(cacheId, maxSize, entries, new AtomicReference<FileLock.State>(null));
            LOG.debug("Creating in-memory store for cache {} (max size: {})", cacheId, maxSize);
            allCaches.put(cacheId, new SoftReference<CacheDetails>(cacheDetails));
            // Retain a strong reference to details for this session
            cachesForThisSession.add(cacheDetails);
            return cacheDetails;
        }
    }

    private Cache<Object, Object> createInMemoryCache(String cacheId, int maxSize) {
        LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize);
        final CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().maximumSize(maxSize).recordStats().removalListener(evictionListener);
        Cache<Object, Object> inMemoryCache = cacheBuilder.build();
        evictionListener.setCache(inMemoryCache);
        return inMemoryCache;
    }

    public void invalidateAll() {
        synchronized (lock) {
            for (SoftReference<CacheDetails> reference : allCaches.values()) {
                CacheDetails cacheDetails = reference.get();
                if (cacheDetails != null) {
                    cacheDetails.entries.invalidateAll();
                }
            }
        }
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
