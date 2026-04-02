/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.cache.FileAccess;
import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.FileLock;
import org.gradle.cache.MultiProcessSafeIndexedCache;
import org.gradle.cache.internal.btree.PersistentIndexedCache;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultMultiProcessSafeIndexedCache<K, V> implements MultiProcessSafeIndexedCache<K, V> {
    private final FileAccess fileAccess;
    private final Supplier<PersistentIndexedCache<K, V>> factory;
    private PersistentIndexedCache<K, V> cache;
    private FileLock.State previousState;

    public DefaultMultiProcessSafeIndexedCache(Supplier<PersistentIndexedCache<K, V>> factory, FileAccess fileAccess) {
        this.factory = factory;
        this.fileAccess = fileAccess;
    }

    @Override
    public String toString() {
        return fileAccess.toString();
    }

    @Override
    public V getIfPresent(final K key) {
        final PersistentIndexedCache<K, V> cache = getCache();
        try {
            return fileAccess.readFile((Supplier<V>) () -> cache.get(key));
        } catch (FileIntegrityViolationException e) {
            return null;
        }
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> producer) {
        V value = getIfPresent(key);
        if (value == null) {
            value = producer.apply(key);
            put(key, value);
        }
        return value;
    }

    @Override
    public void put(final K key, final V value) {
        final PersistentIndexedCache<K, V> cache = getCache();
        // Use writeFile because the cache can internally recover from datafile
        // corruption, so we don't care at this level if it's corrupt
        fileAccess.writeFile(() -> cache.put(key, value));
    }

    @Override
    public void remove(final K key) {
        final PersistentIndexedCache<K, V> cache = getCache();
        // Use writeFile because the cache can internally recover from datafile
        // corruption, so we don't care at this level if it's corrupt
        fileAccess.writeFile(() -> cache.remove(key));
    }

    @Override
    public void finishWork() {
        if (cache != null) {
            // Flush pending data to disk but keep the cache open for reuse.
            // Closing and reopening on every lock cycle is expensive for stores
            // that use mmap (e.g. MapDB) — the mmap setup dominates small builds.
            fileAccess.writeFile(() -> cache.flush());
        }
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
        // Capture state after our own writes so we can detect external modifications
        // on the next afterLockAcquire()
        previousState = currentCacheState;
    }

    @Override
    public void afterLockAcquire(FileLock.State currentCacheState) {
        // If another process modified the cache file since we last released the lock,
        // we must discard our in-process state (stale mmap pages, cached blocks, etc.)
        // and reopen from disk on next access.
        if (cache != null && previousState != null && currentCacheState.hasBeenUpdatedSince(previousState)) {
            try {
                cache.close();
            } finally {
                cache = null;
            }
        }
    }

    /**
     * Returns true if the underlying cache supports concurrent reads from any thread
     * without requiring the coordinator's thread ownership. When true,
     * {@link #getIfPresentDirectly(Object)} can be used to bypass the async worker.
     */
    public boolean supportsConcurrentReads() {
        PersistentIndexedCache<K, V> c = cache;
        return c != null && c.supportsConcurrentReads();
    }

    /**
     * Reads directly from the underlying persistent cache without going through
     * {@code fileAccess.readFile()} (which requires thread ownership).
     *
     * <p>Only safe when the caller already holds the cross-process file lock
     * (e.g. via {@code CrossProcessSynchronizingIndexedCache.withFileLock()})
     * and the underlying cache supports concurrent reads.
     *
     * @return the value, or null if not found or cache not yet initialized
     */
    @Nullable
    public V getIfPresentDirectly(K key) {
        PersistentIndexedCache<K, V> c = cache;
        if (c == null) {
            return null;
        }
        try {
            return c.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private PersistentIndexedCache<K, V> getCache() {
        if (cache == null) {
            // Use writeFile because the cache can internally recover from datafile
            // corruption, so we don't care at this level if it's corrupt
            fileAccess.writeFile(() -> cache = factory.get());
        }
        return cache;
    }
}
