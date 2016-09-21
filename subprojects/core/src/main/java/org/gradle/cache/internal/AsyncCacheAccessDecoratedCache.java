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

import org.gradle.api.Nullable;

import java.util.concurrent.Callable;

public class AsyncCacheAccessDecoratedCache<K, V> implements MultiProcessSafePersistentIndexedCache<K, V> {
    private final AsyncCacheAccess asyncCacheAccess;
    private final MultiProcessSafePersistentIndexedCache<K, V> original;

    public AsyncCacheAccessDecoratedCache(AsyncCacheAccess asyncCacheAccess, MultiProcessSafePersistentIndexedCache<K, V> original) {
        this.asyncCacheAccess = asyncCacheAccess;
        this.original = original;
    }

    @Override
    public void close() {
        original.close();
    }

    @Nullable
    @Override
    public V get(final K key) {
        return asyncCacheAccess.read(new Callable<V>() {
            @Override
            public V call() throws Exception {
                return original.get(key);
            }
        });
    }

    @Override
    public void put(final K key, final V value) {
        asyncCacheAccess.enqueue(new Runnable() {
            @Override
            public void run() {
                original.put(key, value);
            }
        });
    }

    @Override
    public void remove(final K key) {
        asyncCacheAccess.enqueue(new Runnable() {
            @Override
            public void run() {
                original.remove(key);
            }
        });
    }

    @Override
    public void onStartWork(String operationDisplayName, FileLock.State currentCacheState) {
        original.onStartWork(operationDisplayName, currentCacheState);
    }

    @Override
    public void onEndWork(FileLock.State currentCacheState) {
        original.onEndWork(currentCacheState);
    }
}
