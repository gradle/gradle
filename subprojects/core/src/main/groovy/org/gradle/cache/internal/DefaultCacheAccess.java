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
import org.gradle.cache.CacheAccess;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;

public class DefaultCacheAccess implements CacheAccess {
    private final String cacheDiplayName;
    private final File lockFile;
    private final FileLockManager lockManager;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Set<MultiProcessSafePersistentIndexedCache<?, ?>> caches = new HashSet<MultiProcessSafePersistentIndexedCache<?, ?>>();
    private final Lock lock = new ReentrantLock();
    private FileLock fileLock;
    private String operationDisplayName;
    private boolean started;
    private int depth;

    public DefaultCacheAccess(String cacheDiplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDiplayName = cacheDiplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        lock.lock();
        try {
            lockCache(operationDisplayName);
            try {
                boolean wasStarted = onStartWork();
                try {
                    return action.create();
                } finally {
                    if (wasStarted) {
                        onEndWork();
                    }
                }
            } finally {
                unlockCache();
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        lock.lock();
        try {
            if (depth == 0) {
                throw new IllegalStateException("Cannot start long running operation, as the artifact cache has not been locked.");
            }
            boolean wasEnded = onEndWork();
            try {
                return action.create();
            } finally {
                if (wasEnded) {
                    onStartWork();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void longRunningOperation(String operationDisplayName, final Runnable action) {
        longRunningOperation(operationDisplayName, new Factory<Object>() {
            public Object create() {
                action.run();
                return null;
            }
        });
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(cacheFile, keyType, valueType);
            }
        };
        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new MultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
        lock.lock();
        try {
            caches.add(indexedCache);
            if (started) {
                indexedCache.onStartWork(operationDisplayName);
            }
        } finally {
            lock.unlock();
        }
        return indexedCache;
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
        return new BTreePersistentIndexedCache<K, V>(cacheFile, keyType, valueType);
    }

    private void lockCache(String operationDisplayName) {
        if (depth == 0) {
            this.operationDisplayName = operationDisplayName;
        }
        depth++;
    }

    private void unlockCache() {
        depth--;
        if (depth <= 0) {
            operationDisplayName = null;
        }
    }

    private boolean onStartWork() {
        if (started) {
            return false;
        }

        started = true;
        for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
            cache.onStartWork(operationDisplayName);
        }
        return true;
    }

    private boolean onEndWork() {
        if (!started) {
            return false;
        }

        try {
            for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
                cache.onEndWork();
            }
            if (fileLock != null) {
                fileLock.close();
            }
        } finally {
            started = false;
            fileLock = null;
        }
        return true;
    }

    private FileLock getLock() {
        lock.lock();
        try {
            if (!started) {
                throw new IllegalStateException(String.format("The %s has not been locked.", cacheDiplayName));
            }
        } finally {
            lock.unlock();
        }
        if (fileLock == null) {
            fileLock = lockManager.lock(lockFile, Exclusive, cacheDiplayName, operationDisplayName);
        }
        return fileLock;
    }

    private class UnitOfWorkFileAccess extends AbstractFileAccess {
        public <T> T readFromFile(Factory<? extends T> action) throws LockTimeoutException {
            return getLock().readFromFile(action);
        }

        public void writeToFile(Runnable action) throws LockTimeoutException {
            getLock().writeToFile(action);
        }
    }

}
