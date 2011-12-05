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
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;

public class UnitOfWorkCacheManager implements CacheAccess {
    private final String cacheDiplayName;
    private final File lockFile;
    private final FileLockManager lockManager;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Set<MultiProcessSafePersistentIndexedCache<?, ?>> caches = new HashSet<MultiProcessSafePersistentIndexedCache<?, ?>>();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private FileLock fileLock;
    private String operationDisplayName;
    private Thread owner;
    private boolean started;
    private int depth;

    public UnitOfWorkCacheManager(String cacheDiplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDiplayName = cacheDiplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        boolean wasStarted = lockCache(operationDisplayName);
        try {
            if (!wasStarted) {
                onStartWork();
            }
            try {
                return action.create();
            } finally {
                if (!wasStarted) {
                    onEndWork();
                }
            }
        } finally {
            unlockCache(wasStarted);
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        boolean wasStarted = startLongRunningOperation();
        try {
            if (wasStarted) {
                onEndWork();
            }
            try {
                return action.create();
            } finally {
                if (wasStarted) {
                    onStartWork();
                }
            }
        } finally {
            finishLongRunningOperation(wasStarted);
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
        caches.add(indexedCache);
        lock.lock();
        try {
            if (Thread.currentThread() == owner && started) {
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

    private boolean startLongRunningOperation() {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException("Cannot start long running operation, as the artifact cache has not been locked.");
            }
            boolean wasStarted = started;
            started = false;
            return wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private void finishLongRunningOperation(boolean wasStarted) {
        lock.lock();
        try {
            started = wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private boolean lockCache(String operationDisplayName) {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                while (owner != null) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.asUncheckedException(e);
                    }
                }
                this.operationDisplayName = operationDisplayName;
                owner = Thread.currentThread();
            }
            boolean wasStarted = started;
            started = true;
            depth++;
            return wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private void unlockCache(boolean wasStarted) {
        lock.lock();
        try {
            depth--;
            if (!wasStarted) {
                started = false;
                if (depth <= 0) {
                    owner = null;
                    operationDisplayName = null;
                    condition.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void onStartWork() {
        for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
            cache.onStartWork(operationDisplayName);
        }
    }

    private void onEndWork() {
        try {
            for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
                cache.onEndWork();
            }
            if (fileLock != null) {
                fileLock.close();
            }
        } finally {
            fileLock = null;
        }
    }

    private FileLock getLock() {
        lock.lock();
        try {
            if (owner != Thread.currentThread() || !started) {
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
