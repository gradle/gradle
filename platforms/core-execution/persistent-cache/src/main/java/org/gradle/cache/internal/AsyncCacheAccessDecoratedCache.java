/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.cache.AsyncCacheAccess;
import org.gradle.cache.FileLock;
import org.gradle.cache.MultiProcessSafeIndexedCache;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class AsyncCacheAccessDecoratedCache<K, V> implements MultiProcessSafeAsyncPersistentIndexedCache<K, V> {
    private final AsyncCacheAccess asyncCacheAccess;
    private final MultiProcessSafeIndexedCache<K, V> indexedCache;
    private volatile int concurrentReadsState; // 0 = unknown, 1 = true, -1 = false

    public AsyncCacheAccessDecoratedCache(AsyncCacheAccess asyncCacheAccess, MultiProcessSafeIndexedCache<K, V> indexedCache) {
        this.asyncCacheAccess = asyncCacheAccess;
        this.indexedCache = indexedCache;
    }

    private boolean supportsConcurrentReads() {
        int state = concurrentReadsState;
        if (state == 0) {
            state = indexedCache.supportsConcurrentReads() ? 1 : -1;
            concurrentReadsState = state;
        }
        return state == 1;
    }

    @Override
    public String toString() {
        return "{async-cache cache: " + indexedCache + "}";
    }

    @Nullable
    @Override
    public V get(final K key) {
        // When the backing cache supports concurrent reads (e.g. MVStore/MapDB with
        // ConcurrentHashMap write-behind), read directly without queuing through the
        // single worker thread. The caller already holds the cross-process file lock
        // via CrossProcessSynchronizingIndexedCache.withFileLock().
        if (supportsConcurrentReads()) {
            return indexedCache.getIfPresentDirectly(key);
        }
        return asyncCacheAccess.read(() -> indexedCache.getIfPresent(key));
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> producer, Runnable completion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putLater(final K key, final V value, final Runnable completion) {
        try {
            asyncCacheAccess.enqueue(() -> {
                try {
                    indexedCache.put(key, value);
                } finally {
                    completion.run();
                }
            });
        } catch (RuntimeException e) {
            completion.run();
            throw e;
        }
    }

    @Override
    public void removeLater(final K key, final Runnable completion) {
        try {
            asyncCacheAccess.enqueue(() -> {
                try {
                    indexedCache.remove(key);
                } finally {
                    completion.run();
                }
            });
        } catch (RuntimeException e) {
            completion.run();
            throw e;
        }
    }

    @Override
    public void afterLockAcquire(FileLock.State currentCacheState) {
        indexedCache.afterLockAcquire(currentCacheState);
    }

    @Override
    public void finishWork() {
        indexedCache.finishWork();
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
        indexedCache.beforeLockRelease(currentCacheState);
    }
}
