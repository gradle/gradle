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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared;

@ThreadSafe
public class DefaultCacheAccess implements CacheAccess {

    private final static Logger LOG = Logging.getLogger(DefaultCacheAccess.class);

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
    private boolean busy;
    private boolean contended;
    private final ThreadLocal<CacheOperationStack> operationStack = new ThreadLocal<CacheOperationStack>() {
        @Override
        protected CacheOperationStack initialValue() {
            return new CacheOperationStack();
        }
    };
    private int cacheClosedCount;

    public DefaultCacheAccess(String cacheDisplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDiplayName = cacheDisplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
    }

    /**
     * Opens this cache access with the given lock mode. Calling this with {@link org.gradle.cache.internal.FileLockManager.LockMode#Exclusive} will lock the cache for exclusive access from all other
     * threads (including those in this process and all other processes), until {@link #close()} is called.
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
            if (fileLock != null) {
                throw new IllegalStateException("File lock " + lockFile + " is already open.");
            }
            fileLock = lockManager.lock(lockFile, lockMode, cacheDiplayName, whenContended());
            takeOwnership(String.format("Access %s", cacheDiplayName));
        } finally {
            lock.unlock();
        }
    }

    private void closeFileLock() {
        try {
            cacheClosedCount++;
            for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
                cache.close(fileLock);
            }
            fileLock.close();
        } finally {
            fileLock = null;
            contended = false;
        }
    }

    public void close() {
        lock.lock();
        try {
            operationStack.remove();
            lockMode = null;
            owner = null;
            if (fileLock != null) {
                closeFileLock();
            }
        } finally {
            if (cacheClosedCount != 1) {
                LOG.info("Cache {} was closed {} times.", cacheDiplayName, cacheClosedCount);
            }
            lock.unlock();
        }
    }

    public FileLock getFileLock() {
        return fileLock;
    }

    public void useCache(String operationDisplayName, Runnable action) {
        useCache(operationDisplayName, Factories.toFactory(action));
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> factory) {
        if (lockMode == FileLockManager.LockMode.Shared) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        takeOwnership(operationDisplayName);
        try {
            boolean wasStarted = onStartWork();
            try {
                return factory.create();
            } finally {
                if (wasStarted) {
                    onEndWork();
                }
            }
        } finally {
            releaseOwnership(operationDisplayName);
        }
    }

    private void takeOwnership(String operationDisplayName) {
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
            operationStack.get().pushCacheAction(operationDisplayName);
        } finally {
            lock.unlock();
        }
    }

    private void releaseOwnership(String operationDisplayName) {
        lock.lock();
        try {
            operationStack.get().popCacheAction(operationDisplayName);
            if (!operationStack.get().isInCacheAction()) {
                owner = null;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        if (operationStack.get().isInLongRunningOperation()) {
            operationStack.get().pushLongRunningOperation(operationDisplayName);
            try {
                return action.create();
            } finally {
                operationStack.get().popLongRunningOperation(operationDisplayName);
            }
        }

        checkThreadIsOwner();
        boolean wasEnded = onEndWork();
        parkOwner(operationDisplayName);
        try {
            return action.create();
        } finally {
            restoreOwner(operationDisplayName);
            if (wasEnded) {
                onStartWork();
            }
        }
    }

    private void checkThreadIsOwner() {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException(String.format("Cannot start long running operation, as the %s has not been locked.", cacheDiplayName));
            }
        } finally {
            lock.unlock();
        }
    }

    private void parkOwner(String operationDisplayName) {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException(String.format("Cannot start long running operation, as the %s has not been locked.", cacheDiplayName));
            }
            owner = null;
            condition.signalAll();

            operationStack.get().pushLongRunningOperation(operationDisplayName);
        } finally {
            lock.unlock();
        }
    }

    private void restoreOwner(String description) {
        lock.lock();
        try {
            while (owner != null) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            owner = Thread.currentThread();
            operationStack.get().popLongRunningOperation(description);
        } finally {
            lock.unlock();
        }
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        longRunningOperation(operationDisplayName, Factories.toFactory(action));
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
        return newCache(cacheFile, keyType, new DefaultSerializer<V>(valueType.getClassLoader()));
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Class<K> keyType, final Serializer<V> valueSerializer) {
        return newCache(cacheFile, new DefaultSerializer<K>(keyType.getClassLoader()), valueSerializer);
    }

    public <K, V> PersistentIndexedCache<K, V> newCache(final File cacheFile, final Serializer<K> keySerializer, final Serializer<V> valueSerializer) {
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(cacheFile, keySerializer, valueSerializer);
            }
        };
        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new MultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
        lock.lock();
        try {
            caches.add(indexedCache);
            if (fileLock != null) {
                indexedCache.onStartWork(operationStack.get().getDescription());
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
        if (fileLock != null) {
            return false;
        }
        busy = true;

        fileLock = lockManager.lock(lockFile, Exclusive, cacheDiplayName, operationStack.get().getDescription(), whenContended());

        for (MultiProcessSafePersistentIndexedCache<?, ?> cache : caches) {
            cache.onStartWork(operationStack.get().getDescription());
        }
        return true;
    }

    private boolean onEndWork() {
        if (fileLock == null) {
            return false;
        }
        busy = false;

        if (contended || fileLock.getMode() == Shared) {
            closeFileLock();
        }

        return true;
    }

    private FileLock getLock() {
        lock.lock();
        try {
            if (Thread.currentThread() != owner || fileLock == null) {
                throw new IllegalStateException(String.format("The %s has not been locked. File lock available: %s", cacheDiplayName, fileLock != null));
            }
        } finally {
            lock.unlock();
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

    private class CacheOperationStack {
        private final List<CacheOperation> operations = new ArrayList<CacheOperation>();

        public String getDescription() {
            checkNotEmpty();
            return operations.get(0).description;
        }

        public boolean isInLongRunningOperation() {
            return !operations.isEmpty() && operations.get(0).longRunningOperation;
        }

        public void pushLongRunningOperation(String description) {
            operations.add(0, new CacheOperation(description, true));
        }

        public void popLongRunningOperation(String description) {
            pop(description, true);
        }

        public boolean isInCacheAction() {
            return !operations.isEmpty() && !operations.get(0).longRunningOperation;
        }

        public void pushCacheAction(String description) {
            operations.add(0, new CacheOperation(description, false));
        }

        public void popCacheAction(String description) {
            pop(description, false);
        }

        private CacheOperation pop(String description, boolean longRunningOperation) {
            checkNotEmpty();
            CacheOperation operation = operations.remove(0);
            if (operation.description.equals(description) && operation.longRunningOperation == longRunningOperation) {
                return operation;
            }
            throw new IllegalStateException();
        }

        private void checkNotEmpty() {
            if (operations.isEmpty()) {
                throw new IllegalStateException();
            }
        }
    }

    private class CacheOperation {
        final String description;
        final boolean longRunningOperation;

        private CacheOperation(String description, boolean longRunningOperation) {
            this.description = description;
            this.longRunningOperation = longRunningOperation;
        }
    }

    Runnable whenContended() {
        return new Runnable() {
            public void run() {
                lock.lock();
                try {
                    LOG.info("Detected file lock contention of {} (fileLock={}, contended={}, busy={}, owner={})", cacheDiplayName, fileLock != null, contended, busy, owner);
                    if (fileLock == null) {
                        //the lock may have been closed
                        return;
                    }
                    if (owner != null) {
                        contended = true;
                        return;
                    }

                    takeOwnership("Other process requested access to " + cacheDiplayName);
                    try {
                        closeFileLock();
                    } finally {
                        releaseOwnership("Other process requested access to " + cacheDiplayName);
                    }
                } finally {
                    lock.unlock();
                }
            }
        };
    }
}