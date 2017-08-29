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
import com.google.common.util.concurrent.Runnables;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.FileLock;
import org.gradle.cache.internal.MultiProcessSafeAsyncPersistentIndexedCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

class InMemoryDecoratedCache<K, V> implements MultiProcessSafeAsyncPersistentIndexedCache<K, V> {
    private final static Logger LOG = Logging.getLogger(InMemoryDecoratedCache.class);
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
        assert key instanceof String || key instanceof Long || key instanceof File || key instanceof HashCode : "Unsupported key type: " + key;
        Object value;
        try {
            value = inMemoryCache.get(key, new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    Object out = delegate.get(key);
                    return out == null ? NULL : out;
                }
            });
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
        if (value == NULL) {
            return null;
        } else {
            return (V) value;
        }
    }

    @Override
    public V get(final K key, final Transformer<? extends V, ? super K> producer, final Runnable completion) {
        assert key instanceof String || key instanceof Long || key instanceof File || key instanceof HashCode : "Unsupported key type: " + key;
        final AtomicReference<Runnable> completionRef = new AtomicReference<Runnable>(completion);
        Object value;
        try {
            value = inMemoryCache.getIfPresent(key);
            final boolean wasNull = value == NULL;
            if (wasNull) {
                inMemoryCache.invalidate(key);
            } else if (value != null) {
                return (V) value;
            }
            value = inMemoryCache.get(key, new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    if (!wasNull) {
                        Object out = delegate.get(key);
                        if (out != null) {
                            return out;
                        }
                    }
                    V value = producer.transform(key);
                    delegate.putLater(key, value, completion);
                    completionRef.set(Runnables.doNothing());
                    return value;
                }
            });
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } finally {
            completionRef.get().run();
        }
        if (value == NULL) {
            return null;
        } else {
            return (V) value;
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
}
