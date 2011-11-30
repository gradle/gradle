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

import org.gradle.api.internal.Factory;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;

public class UnitOfWorkCacheManager implements UnitOfWorkParticipant {
    private final String cacheDiplayName;
    private final File lockFile;
    private final FileLockManager lockManager;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Set<MultiProcessSafePersistentIndexedCache<?, ?>> caches = new HashSet<MultiProcessSafePersistentIndexedCache<?, ?>>();
    private FileLock lock;
    private boolean working;

    public UnitOfWorkCacheManager(String cacheDiplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDiplayName = cacheDiplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
        if (working) {
            throw new UnsupportedOperationException("Creating a cache inside a unit of work not supported yet.");
        }
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(cacheFile, keyType, valueType);
            }
        };
        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new MultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
        caches.add(indexedCache);
        return indexedCache;
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
        return new BTreePersistentIndexedCache<K, V>(cacheFile, fileAccess, keyType, valueType);
    }

    public void onStartWork() {
        if (working) {
            throw new IllegalStateException("Unit of work has already been started.");
        }
        working = true;
        for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
            cache.onStartWork();
        }
    }

    public void onEndWork() {
        if (!working) {
            throw new IllegalStateException("Unit of work has not been started.");
        }
        try {
            for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
                cache.onEndWork();
            }
            if (lock != null) {
                lock.close();
            }
        } finally {
            lock = null;
            working = false;
        }
    }

    private FileLock getLock() {
        if (lock == null) {
            lock = lockManager.lock(lockFile, Exclusive, cacheDiplayName);
        }
        return lock;
    }

    private void assertInUnitOfWork() {
        if (!working) {
            throw new IllegalStateException("Cannot use cache outside a unit of work.");
        }
    }

    private class UnitOfWorkFileAccess extends AbstractFileAccess {
        public <T> T readFromFile(Factory<? extends T> action) throws LockTimeoutException {
            assertInUnitOfWork();
            return getLock().readFromFile(action);
        }

        public void writeToFile(Runnable action) throws LockTimeoutException {
            assertInUnitOfWork();
            getLock().writeToFile(action);
        }
    }

}
