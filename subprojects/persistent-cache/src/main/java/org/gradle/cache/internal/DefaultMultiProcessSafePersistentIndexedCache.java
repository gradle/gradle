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

import org.gradle.api.Transformer;
import org.gradle.cache.FileAccess;
import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.FileLock;
import org.gradle.cache.MultiProcessSafePersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.internal.Factory;

public class DefaultMultiProcessSafePersistentIndexedCache<K, V> implements MultiProcessSafePersistentIndexedCache<K, V> {
    private final FileAccess fileAccess;
    private final Factory<BTreePersistentIndexedCache<K, V>> factory;
    private BTreePersistentIndexedCache<K, V> cache;

    public DefaultMultiProcessSafePersistentIndexedCache(Factory<BTreePersistentIndexedCache<K, V>> factory, FileAccess fileAccess) {
        this.factory = factory;
        this.fileAccess = fileAccess;
    }

    @Override
    public String toString() {
        return fileAccess.toString();
    }

    @Override
    public V get(final K key) {
        final BTreePersistentIndexedCache<K, V> cache = getCache();
        try {
            return fileAccess.readFile(new Factory<V>() {
                public V create() {
                    return cache.get(key);
                }
            });
        } catch (FileIntegrityViolationException e) {
            return null;
        }
    }

    @Override
    public V get(K key, Transformer<? extends V, ? super K> producer) {
        V value = get(key);
        if (value == null) {
            value = producer.transform(key);
            put(key, value);
        }
        return value;
    }

    @Override
    public void put(final K key, final V value) {
        final BTreePersistentIndexedCache<K, V> cache = getCache();
        // Use writeFile because the cache can internally recover from datafile
        // corruption, so we don't care at this level if it's corrupt
        fileAccess.writeFile(new Runnable() {
            public void run() {
                cache.put(key, value);
            }
        });
    }

    @Override
    public void remove(final K key) {
        final BTreePersistentIndexedCache<K, V> cache = getCache();
        // Use writeFile because the cache can internally recover from datafile
        // corruption, so we don't care at this level if it's corrupt
        fileAccess.writeFile(new Runnable() {
            public void run() {
                cache.remove(key);
            }
        });
    }

    @Override
    public void afterLockAcquire(FileLock.State currentCacheState) {
    }

    @Override
    public void finishWork() {
        if (cache != null) {
            try {
                fileAccess.writeFile(new Runnable() {
                    public void run() {
                        cache.close();
                    }
                });
            } finally {
                cache = null;
            }
        }
    }

    @Override
    public void beforeLockRelease(FileLock.State currentCacheState) {
    }

    private BTreePersistentIndexedCache<K, V> getCache() {
        if (cache == null) {
            // Use writeFile because the cache can internally recover from datafile
            // corruption, so we don't care at this level if it's corrupt
            fileAccess.writeFile(new Runnable() {
                public void run() {
                    cache = factory.create();
                }
            });
        }
        return cache;
    }
}
