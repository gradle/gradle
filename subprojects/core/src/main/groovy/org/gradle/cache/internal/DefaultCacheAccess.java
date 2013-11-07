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
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.serialize.Serializer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared;

@ThreadSafe
public class DefaultCacheAccess implements CacheAccess {

    private final static Logger LOG = Logging.getLogger(DefaultCacheAccess.class);

    private final String cacheDisplayName;
    private final File lockFile;
    private final FileLockManager lockManager;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Set<MultiProcessSafePersistentIndexedCache> caches = new HashSet<MultiProcessSafePersistentIndexedCache>();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Thread owner;
    private LockOptions lockOptions;
    private FileLock fileLock;
    private FileLock.State stateAtOpen;
    private boolean contended;
    private final CacheAccessOperationsStack operations;
    private int cacheClosedCount;

    public DefaultCacheAccess(String cacheDisplayName, File lockFile, FileLockManager lockManager) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockFile = lockFile;
        this.lockManager = lockManager;
        this.operations = new CacheAccessOperationsStack();
    }

    /**
     * Opens this cache access with the given lock mode. Calling this with {@link org.gradle.cache.internal.FileLockManager.LockMode#Exclusive} will lock the cache for exclusive access from all other
     * threads (including those in this process and all other processes), until {@link #close()} is called.
     * @param lockOptions
     */
    public void open(LockOptions lockOptions) {
        lock.lock();
        try {
            if (owner != null) {
                throw new IllegalStateException(String.format("Cannot open the %s, as it is already in use.", cacheDisplayName));
            }
            this.lockOptions = lockOptions;
            if (lockOptions.getMode() == FileLockManager.LockMode.None) {
                return;
            }
            if (fileLock != null) {
                throw new IllegalStateException("File lock " + lockFile + " is already open.");
            }
            fileLock = lockManager.lock(lockFile, lockOptions, cacheDisplayName);
            stateAtOpen = fileLock.getState();
            takeOwnership(String.format("Access %s", cacheDisplayName));
            lockManager.allowContention(fileLock, whenContended());
        } finally {
            lock.unlock();
        }
    }

    private void closeFileLock() {
        try {
            cacheClosedCount++;
            try {
                // Close the caches and then notify them of the final state, in case the caches do work on close
                new CompositeStoppable().add(caches).stop();
                FileLock.State state = fileLock.getState();
                for (MultiProcessSafePersistentIndexedCache cache : caches) {
                    cache.onEndWork(state);
                }
            } finally {
                fileLock.close();
            }
        } finally {
            fileLock = null;
            stateAtOpen = null;
            contended = false;
        }
    }

    public void close() {
        lock.lock();
        try {
            if (fileLock != null) {
                closeFileLock();
            }
            if (cacheClosedCount != 1) {
                LOG.debug("Cache {} was closed {} times.", cacheDisplayName, cacheClosedCount);
            }
        } finally {
            lockOptions = null;
            owner = null;
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
        if (lockOptions != null && lockOptions.getMode() == FileLockManager.LockMode.Shared) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        takeOwnership(operationDisplayName);
        boolean wasStarted = false;
        try {
            wasStarted = onStartWork();
            return factory.create();
        } finally {
            lock.lock();
            try {
                try {
                    if (wasStarted) {
                        onEndWork();
                    }
                } finally {
                    releaseOwnership();
                }
            } finally {
                lock.unlock();
            }
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
            operations.pushCacheAction(operationDisplayName);
        } finally {
            lock.unlock();
        }
    }

    private void releaseOwnership() {
        lock.lock();
        try {
            operations.popCacheAction();
            if (!operations.isInCacheAction()) {
                owner = null;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        boolean wasEnded = startLongRunningOperation(operationDisplayName);
        try {
            return action.create();
        } finally {
            finishLongRunningOperation(wasEnded);
        }
    }

    private boolean startLongRunningOperation(String operationDisplayName) {
        boolean wasEnded;
        lock.lock();
        try {
            if (operations.isInCacheAction()) {
                checkThreadIsOwner();
                wasEnded = onEndWork();
                owner = null;
                condition.signalAll();
            } else {
                wasEnded = false;
            }
            operations.pushLongRunningOperation(operationDisplayName);
        } finally {
            lock.unlock();
        }
        return wasEnded;
    }

    private void finishLongRunningOperation(boolean wasEnded) {
        lock.lock();
        try {
            operations.popLongRunningOperation();
            if (operations.isInCacheAction()) {
                restoreOwner();
                if (wasEnded) {
                    onStartWork();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkThreadIsOwner() {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException(String.format("Cannot start long running operation, as the %s has not been locked.", cacheDisplayName));
            }
        } finally {
            lock.unlock();
        }
    }

    private void restoreOwner() {
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
        } finally {
            lock.unlock();
        }
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        longRunningOperation(operationDisplayName, Factories.toFactory(action));
    }

    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> newCache(final PersistentIndexedCacheParameters<K, V> parameters) {
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(parameters.getCacheFile(), parameters.getKeySerializer(), parameters.getValueSerializer());
            }
        };
        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = parameters.decorate(
                new DefaultMultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess));

        lock.lock();
        try {
            caches.add(indexedCache);
            if (fileLock != null) {
                indexedCache.onStartWork(operations.getDescription(), stateAtOpen);
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
        fileLock = lockManager.lock(lockFile, lockOptions.withMode(Exclusive), cacheDisplayName, operations.getDescription());
        stateAtOpen = fileLock.getState();
        for (UnitOfWorkParticipant cache : caches) {
            cache.onStartWork(operations.getDescription(), stateAtOpen);
        }

        lockManager.allowContention(fileLock, whenContended());

        return true;
    }

    private boolean onEndWork() {
        if (fileLock == null) {
            return false;
        }
        if (contended || fileLock.getMode() == Shared) {
            closeFileLock();
        }
        return true;
    }

    private FileLock getLock() {
        lock.lock();
        try {
            if ((Thread.currentThread() != owner && owner != null) || fileLock == null) {
                throw new IllegalStateException(String.format("The %s has not been locked for this thread. File lock: %s, owner: %s", cacheDisplayName, fileLock != null, owner));
            }
        } finally {
            lock.unlock();
        }
        return fileLock;
    }

    private class UnitOfWorkFileAccess extends AbstractFileAccess {
        @Override
        public String toString() {
            return cacheDisplayName;
        }

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

    Runnable whenContended() {
        return new Runnable() {
            public void run() {
                lock.lock();
                try {
                    LOG.debug("Detected file lock contention of {} (fileLock={}, contended={}, owner={})", cacheDisplayName, fileLock != null, contended, owner);
                    if (fileLock == null) {
                        //the lock may have been closed
                        return;
                    }
                    if (owner != null) {
                        contended = true;
                        return;
                    }

                    takeOwnership("Other process requested access to " + cacheDisplayName);
                    try {
                        closeFileLock();
                    } finally {
                        releaseOwnership();
                    }
                } finally {
                    lock.unlock();
                }
            }
        };
    }

    Thread getOwner() {
        return owner;
    }

    FileAccess getFileAccess() {
        return fileAccess;
    }
}