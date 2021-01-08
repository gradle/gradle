/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.cache.CrossProcessCacheAccess;
import org.gradle.cache.FileLock;
import org.gradle.cache.MultiProcessSafePersistentIndexedCache;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Applies cross-process file locking to a backing cache, to ensure that any in-memory and on file state is kept in sync while this process is read from or writing to the cache.
 */
public class CrossProcessSynchronizingCache<K, V> implements MultiProcessSafePersistentIndexedCache<K, V> {
    private final CrossProcessCacheAccess cacheAccess;
    private final MultiProcessSafeAsyncPersistentIndexedCache<K, V> target;

    public CrossProcessSynchronizingCache(MultiProcessSafeAsyncPersistentIndexedCache<K, V> target, CrossProcessCacheAccess cacheAccess) {
        this.target = target;
        this.cacheAccess = cacheAccess;
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Nullable
    @Override
    public V getIfPresent(final K key) {
        return cacheAccess.withFileLock(() -> target.get(key));
    }

    @Override
    public V get(final K key, final Function<? super K, ? extends V> producer) {
        Runnable runnable = cacheAccess.acquireFileLock();
        return target.get(key, producer, runnable);
    }

    @Override
    public void put(K key, V value) {
        Runnable runnable = cacheAccess.acquireFileLock();
        target.putLater(key, value, runnable);
    }

    @Override
    public void remove(K key) {
        Runnable runnable = cacheAccess.acquireFileLock();
        target.removeLater(key, runnable);
    }

    @Override
    public void afterLockAcquire(FileLock.State currentCacheState) {
        target.afterLockAcquire(currentCacheState);
    }

    @Override
    public void finishWork() {
        target.finishWork();
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
        target.beforeLockRelease(currentCacheState);
    }
}
