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

import com.google.common.cache.Cache;
import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.cache.FileLock;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class InMemoryDecoratedCache<K, V> implements MultiProcessSafeAsyncPersistentIndexedCache<K, V>, InMemoryCacheController {
    private final static Logger LOG = LoggerFactory.getLogger(InMemoryDecoratedCache.class);
    private final static Object NULL = new Object();
    private final MultiProcessSafeAsyncPersistentIndexedCache<K, V> delegate;
    private final Cache<Object, Object> inMemoryCache;
    private final String cacheId;
    private final AtomicReference<FileLock.State> fileLockStateReference;

    public InMemoryDecoratedCache(MultiProcessSafeAsyncPersistentIndexedCache<K, V> delegate, Cache<Object, Object> inMemoryCache, String cacheId, AtomicReference<FileLock.State> fileLockStateReference) {
        this.delegate = delegate;
        this.inMemoryCache = inMemoryCache;
        this.cacheId = cacheId;
        this.fileLockStateReference = fileLockStateReference;
    }

    @Override
    public String toString() {
        return "{in-memory-cache cache: " + delegate + "}";
    }

    @Override
    public V get(final K key) {
        Object value;
        try {
            value = inMemoryCache.get(key, () -> {
                Object out = delegate.get(key);
                return out == null ? NULL : out;
            });
        } catch (UncheckedExecutionException | ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
        if (value == NULL) {
            return null;
        } else {
            return Cast.uncheckedCast(value);
        }
    }

    @Override
    public V get(final K key, final Function<? super K, ? extends V> producer, final Runnable completion) {
        final AtomicReference<Runnable> completionRef = new AtomicReference<>(completion);
        Object value;
        try {
            value = inMemoryCache.getIfPresent(key);
            final boolean wasNull = value == NULL;
            if (wasNull) {
                inMemoryCache.invalidate(key);
            } else if (value != null) {
                return Cast.uncheckedCast(value);
            }
            value = inMemoryCache.get(key, () -> {
                if (!wasNull) {
                    Object out = delegate.get(key);
                    if (out != null) {
                        return out;
                    }
                }
                V generatedValue = producer.apply(key);
                delegate.putLater(key, generatedValue, completion);
                completionRef.set(Runnables.doNothing());
                return generatedValue;
            });
        } catch (UncheckedExecutionException | ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } finally {
            completionRef.get().run();
        }
        if (value == NULL) {
            return null;
        } else {
            return Cast.uncheckedCast(value);
        }
    }

    @Override
    public void putLater(K key, V value, Runnable completion) {
        inMemoryCache.put(key, value);
        delegate.putLater(key, value, completion);
    }

    @Override
    public void removeLater(K key, Runnable completion) {
        inMemoryCache.put(key, NULL);
        delegate.removeLater(key, completion);
    }

    @Override
    public void afterLockAcquire(FileLock.State currentCacheState) {
        boolean outOfDate = false;
        FileLock.State previousState = fileLockStateReference.get();
        if (previousState == null) {
            outOfDate = true;
        } else if (currentCacheState.hasBeenUpdatedSince(previousState)) {
            LOG.info("Invalidating in-memory cache of {}", cacheId);
            outOfDate = true;
        }
        if (outOfDate) {
            inMemoryCache.invalidateAll();
        }
        delegate.afterLockAcquire(currentCacheState);
    }

    @Override
    public void finishWork() {
        delegate.finishWork();
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
        fileLockStateReference.set(currentCacheState);
        delegate.beforeLockRelease(currentCacheState);
    }

    @Override
    public String getCacheId() {
        return cacheId;
    }

    @Override
    public void clearInMemoryCache() {
        inMemoryCache.invalidateAll();
    }
}
