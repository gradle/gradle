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

import org.gradle.api.Nullable;
import org.gradle.internal.Factory;

public class CrossProcessSynchronizingCache<K, V> implements MultiProcessSafePersistentIndexedCache<K, V> {
    private final CrossProcessCacheAccess cacheAccess;
    private final MultiProcessSafeAsyncPersistentIndexedCache<K, V> target;

    public CrossProcessSynchronizingCache(MultiProcessSafeAsyncPersistentIndexedCache<K, V> target, CrossProcessCacheAccess cacheAccess) {
        this.target = target;
        this.cacheAccess = cacheAccess;
    }

    @Nullable
    @Override
    public V get(final K key) {
        return cacheAccess.withFileLock(new Factory<V>() {
            @Override
            public V create() {
                return target.get(key);
            }
        });
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
