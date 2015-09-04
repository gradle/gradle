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
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.Serializer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.FileLockManager.LockMode.Shared;

@ThreadSafe
public class DefaultCacheAccess implements CacheCoordinator {

    private final static Logger LOG = Logging.getLogger(DefaultCacheAccess.class);

    private final String cacheDisplayName;
    private final File lockTarget;
    private final File baseDir;
    private final FileLockManager lockManager;
    private final CacheInitializationAction initializationAction;
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

    public DefaultCacheAccess(String cacheDisplayName, File lockTarget, File baseDir, FileLockManager lockManager, CacheInitializationAction initializationAction) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockTarget = lockTarget;
        this.baseDir = baseDir;
        this.lockManager = lockManager;
        this.initializationAction = initializationAction;
        this.operations = new CacheAccessOperationsStack();
    }

    public void open(LockOptions lockOptions) {
        lock.lock();
        try {
            if (this.lockOptions != null) {
                throw new IllegalStateException(String.format("Cannot open the %s, as it has already been opened.", cacheDisplayName));
            }
            this.lockOptions = lockOptions;
            if (lockOptions.getMode() == FileLockManager.LockMode.None) {
                return;
            }
            if (fileLock != null) {
                throw new IllegalStateException("File lock " + lockTarget + " is already open.");
            }
            fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName);

            boolean rebuild = initializationAction.requiresInitialization(fileLock);
            if (rebuild) {
                if (lockOptions.getMode() == Exclusive) {
                    fileLock.writeFile(new Runnable() {
                        public void run() {
                            initializationAction.initialize(fileLock);
                        }
                    });
                } else {
                    for (int tries = 0; rebuild && tries < 3; tries++) {
                        fileLock.close();
                        fileLock = lockManager.lock(lockTarget, lockOptions.withMode(Exclusive), cacheDisplayName, "Initialize cache");
                        rebuild = initializationAction.requiresInitialization(fileLock);
                        if (rebuild) {
                            fileLock.writeFile(new Runnable() {
                                public void run() {
                                    initializationAction.initialize(fileLock);
                                }
                            });
                        }
                        fileLock.close();
                        fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName);
                        rebuild = initializationAction.requiresInitialization(fileLock);
                    }
                    if (rebuild) {
                        throw new CacheOpenException(String.format("Failed to initialize %s", cacheDisplayName));
                    }
                }
            }

            stateAtOpen = fileLock.getState();
            takeOwnership(String.format("Access %s", cacheDisplayName));
        } catch (Throwable throwable) {
            if (fileLock != null) {
                fileLock.close();
                fileLock = null;
            }
            throw UncheckedException.throwAsUncheckedException(throwable);
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
            // Take ownership
            if (owner == null) {
                owner = Thread.currentThread();
            } else if (lockOptions.getMode() != Shared && owner != Thread.currentThread()) {
                // TODO:ADAM - The check for shared mode is a work around. Owner should release the lock
                throw new IllegalStateException(String.format("Cannot close %s as it is currently being used by another thread.", cacheDisplayName));
            }
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

    public void useCache(String operationDisplayName, Runnable action) {
        useCache(operationDisplayName, Factories.toFactory(action));
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> factory) {
        if (lockOptions != null && lockOptions.getMode() == FileLockManager.LockMode.Shared) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        boolean wasStarted = false;
        lock.lock();
        try {
            takeOwnership(operationDisplayName);
            wasStarted = onStartWork();
        } finally {
            lock.unlock();
        }
        try {
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
            if (lockOptions == null || lockOptions.getMode() == Shared) {
                throw new UnsupportedOperationException("Not supported for this lock mode.");
            }
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
        final File cacheFile = new File(baseDir, parameters.getCacheName() + ".bin");
        Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
            public BTreePersistentIndexedCache<K, V> create() {
                return doCreateCache(cacheFile, parameters.getKeySerializer(), parameters.getValueSerializer());
            }
        };

        MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new DefaultMultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
        CacheDecorator decorator = parameters.getCacheDecorator();
        indexedCache = decorator == null ? indexedCache : decorator.decorate(cacheFile.getAbsolutePath(), parameters.getCacheName(), indexedCache);

        lock.lock();
        try {
            caches.add(indexedCache);
            if (fileLock != null) {
                String description = operations.isInCacheAction() ? operations.getDescription() : "cache creation";
                indexedCache.onStartWork(description, stateAtOpen);
            }
        } finally {
            lock.unlock();
        }
        return indexedCache;
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return new BTreePersistentIndexedCache<K, V>(cacheFile, keySerializer, valueSerializer);
    }

    private boolean onStartWork() {
        if (fileLock != null) {
            return false;
        }
        fileLock = lockManager.lock(lockTarget, lockOptions.withMode(Exclusive), cacheDisplayName, operations.getDescription());
        if (initializationAction.requiresInitialization(fileLock)) {
            fileLock.writeFile(new Runnable() {
                public void run() {
                    initializationAction.initialize(fileLock);
                }
            });
        }
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
            if (Thread.currentThread() != owner) {
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