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
import org.gradle.cache.MultiProcessSafePersistentIndexedCache;

import javax.annotation.Nullable;
import java.util.function.Function;

public class AsyncCacheAccessDecoratedCache<K, V> implements MultiProcessSafeAsyncPersistentIndexedCache<K, V> {
    private final AsyncCacheAccess asyncCacheAccess;
    private final MultiProcessSafePersistentIndexedCache<K, V> persistentCache;

    public AsyncCacheAccessDecoratedCache(AsyncCacheAccess asyncCacheAccess, MultiProcessSafePersistentIndexedCache<K, V> persistentCache) {
        this.asyncCacheAccess = asyncCacheAccess;
        this.persistentCache = persistentCache;
    }

    @Override
    public String toString() {
        return "{async-cache cache: " + persistentCache + "}";
    }

    @Nullable
    @Override
    public V get(final K key) {
        return asyncCacheAccess.read(() -> persistentCache.getIfPresent(key));
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
                    persistentCache.put(key, value);
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
                    persistentCache.remove(key);
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
        persistentCache.afterLockAcquire(currentCacheState);
    }

    @Override
    public void finishWork() {
        persistentCache.finishWork();
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
        persistentCache.beforeLockRelease(currentCacheState);
    }
}
