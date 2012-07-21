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

import net.jcip.annotations.ThreadSafe;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;

@ThreadSafe
public class DefaultCacheAccess implements CacheAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheAccess.class);

    private final String cacheDiplayName;
    private final File lockFile;
    private final FileLockManager lockManager;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Set<MultiProcessSafePersistentIndexedCache<?, ?>> caches = new HashSet<MultiProcessSafePersistentIndexedCache<?, ?>>();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Thread owner;
    private FileLockManager.LockMode lockMode;
    private FileLock fileLock;
    private boolean started;
    private final List<String> operationStack = new ArrayList<String>();


    public DefaultCacheAccess(String cacheDisplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDiplayName = cacheDisplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
    }

    /**
     * Opens this cache access with the given lock mode. Calling this with {@link org.gradle.cache.internal.FileLockManager.LockMode#Exclusive} will
     * lock the cache for exclusive access from all other threads (including those in this process and all other processes), until
     * {@link #close()} is called.
     */
    public void open(FileLockManager.LockMode lockMode) {
        lock.lock();
        try {
            if (owner != null) {
                throw new IllegalStateException(String.format("Cannot open the %s, as it is already in use.", cacheDiplayName));
            }
            this.lockMode = lockMode;
            if (lockMode == FileLockManager.LockMode.None) {
                return;
            }
            started = true;
            fileLock = lockManager.lock(lockFile, lockMode, cacheDiplayName);
            lockCache(String.format("Access %s", cacheDiplayName));
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
                cache.close();
            }
            operationStack.clear();
            started = false;
            lockMode = null;
            owner = null;
            if (fileLock != null) {
                try {
                    fileLock.close();
                } finally {
                    fileLock = null;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public FileLock getFileLock() {
        return fileLock;
    }

    public void useCache(String operationDisplayName, final Runnable action) {
        useCache(operationDisplayName, new Factory<Object>() {
            public Object create() {
                action.run();
                return null;
            }
        });
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        if (lockMode == FileLockManager.LockMode.Shared) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

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
    }

    private void lockCache(String operationDisplayName) {
        lock.lock();
        try {
            while (owner != null && owner != Thread.currentThread()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            owner = Thread.currentThread();
            operationStack.add(0, operationDisplayName);
        } finally {
            lock.unlock();
        }
    }

    private void unlockCache() {
        lock.lock();
        try {
            operationStack.remove(0);
            if (operationStack.isEmpty()) {
                owner = null;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        LOGGER.debug(operationDisplayName + " (" + Thread.currentThread() + ") - start long running operation");
        try {
            boolean wasEnded = onEndWork();
            try {
                List<String> parkedOperationStack = startLongRunningOperation();
                try {
                    return action.create();
                } finally {
                    endLongRunningOperation(parkedOperationStack);
                }
            } finally {
                if (wasEnded) {
                    onStartWork();
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } finally {
            LOGGER.debug(operationDisplayName + " (" + Thread.currentThread() + ") - end long running operation");
        }
    }

    private List<String> startLongRunningOperation() {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException(String.format("Cannot start long running operation, as the %s has not been locked.", cacheDiplayName));
            }
            owner = null;
            condition.signalAll();
            List<String> parkedOperationStack = new ArrayList<String>(operationStack);
            operationStack.clear();
            return parkedOperationStack;
        } finally {
            lock.unlock();
        }
    }

    private void endLongRunningOperation(List<String> parkedOperationStack) {
        lock.lock();
        try {
            while (owner != null) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (!operationStack.isEmpty()) {
                throw new IllegalStateException("OperationStack not empty");
            }
            owner = Thread.currentThread();
            operationStack.addAll(parkedOperationStack);
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
        return newCache(cacheFile, keyType, new DefaultSerializer<V>(valueType.getClassLoader()));
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Class<K> keyType, final Serializer<V> valueSerializer) {
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(cacheFile, new DefaultSerializer<K>(keyType.getClassLoader()), valueSerializer);
            }
        };
        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new MultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
        lock.lock();
        try {
            caches.add(indexedCache);
            if (started) {
                indexedCache.onStartWork(operationStack.get(0));
            }
        } finally {
            lock.unlock();
        }
        return indexedCache;
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(final File cacheFile, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) {
        return new BTreePersistentIndexedCache<K, V>(cacheFile, keySerializer, valueSerializer);
    }

    private boolean onStartWork() {
        if (started) {
            return false;
        }

        started = true;
        for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
            cache.onStartWork(operationStack.get(0));
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
            if (Thread.currentThread() != owner || !started) {
                throw new IllegalStateException(String.format("The %s has not been locked.", cacheDiplayName));
            }
        } finally {
            lock.unlock();
        }
        if (fileLock == null) {
            fileLock = lockManager.lock(lockFile, Exclusive, cacheDiplayName, operationStack.get(0));
        }
        return fileLock;
    }

    private class UnitOfWorkFileAccess extends AbstractFileAccess {
        public <T> T readFile(Factory<? extends T> action) throws LockTimeoutException {
            return getLock().readFile(action);
        }

        public void updateFile(Runnable action) throws LockTimeoutException {
            getLock().updateFile(action);
        }

        public void writeFile(Runnable action) throws LockTimeoutException {
            getLock().writeFile(action);
        }
    }
}
