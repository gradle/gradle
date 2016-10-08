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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.AsyncCacheAccess;
import org.gradle.cache.internal.AsyncCacheAccessDecoratedCache;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link CacheDecorator} that wraps each cache with an in-memory cache that is used to short-circuit reads from the backing cache.
 * The in-memory cache is invalidated when the backing cache is changed by another process.
 */
public class InMemoryTaskArtifactCache implements CacheDecorator {
    private final static Logger LOG = Logging.getLogger(InMemoryTaskArtifactCache.class);
    private final Cache<String, Cache<Object, Object>> cache;
    private final Map<String, AtomicReference<FileLock.State>> fileLockStates = new HashMap<String, AtomicReference<FileLock.State>>();
    private final CacheCapSizer cacheCapSizer;

    public InMemoryTaskArtifactCache() {
        this(new CacheCapSizer());
    }

    InMemoryTaskArtifactCache(CacheCapSizer cacheCapSizer) {
        this.cacheCapSizer = cacheCapSizer;
        final CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(cacheCapSizer.getNumberOfCaches() * 2);
        this.cache = cacheBuilder //X2 to factor in a child build (for example buildSrc)
                .build();
    }

    public synchronized <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, AsyncCacheAccess asyncCacheAccess) {
        return createInMemoryDecoratedCache(asyncCacheAccess, persistentCache, createInMemoryCache(cacheId, cacheName), cacheId, getFileLockStateReference(cacheId));
    }

    private <K, V> InMemoryDecoratedCache<K, V> createInMemoryDecoratedCache(AsyncCacheAccess asyncCacheAccess, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, Cache<Object, Object> inMemoryCache, String cacheId, AtomicReference<FileLock.State> fileLockStateReference) {
        return new InMemoryDecoratedCache<K, V>(new AsyncCacheAccessDecoratedCache<K, V>(asyncCacheAccess, persistentCache), inMemoryCache, cacheId, fileLockStateReference);
    }

    private AtomicReference<FileLock.State> getFileLockStateReference(String cacheId) {
        AtomicReference<FileLock.State> fileLockStateReference = fileLockStates.get(cacheId);
        if (fileLockStateReference == null) {
            fileLockStateReference = new AtomicReference<FileLock.State>(null);
            fileLockStates.put(cacheId, fileLockStateReference);
        }
        return fileLockStateReference;
    }

    private Cache<Object, Object> createInMemoryCache(String cacheId, String cacheName) {
        Cache<Object, Object> inMemoryCache = this.cache.getIfPresent(cacheId);
        if (inMemoryCache != null) {
            LOG.info("In-memory cache of {}: Size{{}}, {}", cacheId, inMemoryCache.size(), inMemoryCache.stats());
        } else {
            Integer maxSize = cacheCapSizer.getMaxSize(cacheName);
            assert maxSize != null : "Unknown cache.";
            LOG.debug("Creating In-memory cache of {}: MaxSize{{}}", cacheId, maxSize);
            LoggingEvictionListener evictionListener = new LoggingEvictionListener(cacheId, maxSize);
            final CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder().maximumSize(maxSize).recordStats().removalListener(evictionListener);
            configureInMemoryCache(cacheBuilder, cacheId, cacheName);
            inMemoryCache = cacheBuilder.build();
            evictionListener.setCache(inMemoryCache);
            this.cache.put(cacheId, inMemoryCache);
        }
        return inMemoryCache;
    }

    protected void configureInMemoryCache(CacheBuilder<Object, Object> cacheBuilder, String cacheId, String cacheName) {

    }

    public void invalidateAll() {
        for(Cache<Object, Object> subcache : cache.asMap().values()) {
            subcache.invalidateAll();
        }
    }

    public void onFlush() {
        // do nothing by default on flush
    }
}
