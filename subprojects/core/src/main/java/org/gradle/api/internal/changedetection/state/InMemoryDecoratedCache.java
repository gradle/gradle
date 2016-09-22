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

package org.gradle.api.internal.changedetection.state;

import com.google.common.cache.Cache;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.AsyncCacheAccess;
import org.gradle.cache.internal.AsyncCacheAccessDecoratedCache;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.MultiProcessSafePersistentIndexedCache;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

class InMemoryDecoratedCache<K, V> extends AsyncCacheAccessDecoratedCache<K, V> {
    private final static Logger LOG = Logging.getLogger(InMemoryDecoratedCache.class);
    private final static Object NULL = new Object();
    private final Cache<Object, Object> data;
    private final String cacheId;
    private final AtomicReference<FileLock.State> fileLockStateReference;

    public InMemoryDecoratedCache(AsyncCacheAccess asyncCacheAccess, MultiProcessSafePersistentIndexedCache<K, V> original, Cache<Object, Object> data, String cacheId, AtomicReference<FileLock.State> fileLockStateReference) {
        super(asyncCacheAccess, original);
        this.data = data;
        this.cacheId = cacheId;
        this.fileLockStateReference = fileLockStateReference;
    }

    public V get(final K key) {
        assert key instanceof String || key instanceof Long || key instanceof File : "Unsupported key type: " + key;
        Object value;
        try {
            value = data.get(key, new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    Object out = InMemoryDecoratedCache.super.get(key);
                    return out == null ? NULL : out;
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        if (value == NULL) {
            return null;
        } else {
            return (V) value;
        }
    }

    public void put(final K key, final V value) {
        data.put(key, value);
        super.put(key, value);
    }

    public void remove(final K key) {
        data.put(key, NULL);
        super.remove(key);
    }

    public void onStartWork(String operationDisplayName, FileLock.State currentCacheState) {
        boolean outOfDate = false;
        FileLock.State previousState = fileLockStateReference.get();
        if (previousState == null) {
            outOfDate = true;
        } else if (currentCacheState.hasBeenUpdatedSince(previousState)) {
            LOG.info("Invalidating in-memory cache of {}", cacheId);
            outOfDate = true;
        }
        if (outOfDate) {
            data.invalidateAll();
        }
        super.onStartWork(operationDisplayName, currentCacheState);
    }

    public void onEndWork(FileLock.State currentCacheState) {
        fileLockStateReference.set(currentCacheState);
        super.onEndWork(currentCacheState);
    }
}
