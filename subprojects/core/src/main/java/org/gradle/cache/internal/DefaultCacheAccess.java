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

import com.google.common.base.Objects;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.FileLockManager.LockMode.None;

@ThreadSafe
public class DefaultCacheAccess implements CacheCoordinator {
    private final static Logger LOG = Logging.getLogger(DefaultCacheAccess.class);
    private final String cacheDisplayName;
    private final File baseDir;
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final FileAccess fileAccess = new UnitOfWorkFileAccess();
    private final Map<String, IndexedCacheEntry> caches = new HashMap<String, IndexedCacheEntry>();
    private final AbstractCrossProcessCacheAccess crossProcessCacheAccess;
    private final LockOptions lockOptions;

    private StoppableExecutor cacheUpdateExecutor;
    private CacheAccessWorker cacheAccessWorker;

    private final Lock lock = new ReentrantLock(); // protects the following state
    private final Condition condition = lock.newCondition();
    private boolean open;
    private Thread owner;
    private FileLock fileLock;
    private FileLock.State stateAtOpen;
    private Runnable fileLockHeldByOwner;
    private boolean contended;
    private final CacheAccessOperationsStack operations;
    private int cacheClosedCount;

    public DefaultCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, File baseDir, FileLockManager lockManager, CacheInitializationAction initializationAction, ExecutorFactory executorFactory) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockOptions = lockOptions;
        this.baseDir = baseDir;
        this.lockManager = lockManager;
        this.executorFactory = executorFactory;
        this.operations = new CacheAccessOperationsStack();

        Action<FileLock> onFileLockAcquireAction = new Action<FileLock>() {
            @Override
            public void execute(FileLock fileLock) {
                afterLockAcquire(fileLock);
            }
        };
        Action<FileLock> onFileLockReleaseAction = new Action<FileLock>() {
            @Override
            public void execute(FileLock fileLock) {
                beforeLockRelease(fileLock);
            }
        };

        switch (lockOptions.getMode()) {
            case Shared:
                crossProcessCacheAccess = new FixedSharedModeCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions, lockManager, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                break;
            case Exclusive:
                crossProcessCacheAccess = new FixedExclusiveModeCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions, lockManager, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                break;
            case None:
                crossProcessCacheAccess = new LockOnDemandCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions.withMode(Exclusive), lockManager, lock, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private synchronized AsyncCacheAccess getCacheAccessWorker() {
        if (cacheAccessWorker == null) {
            cacheAccessWorker = new CacheAccessWorker(cacheDisplayName, this);
            cacheUpdateExecutor = executorFactory.create("Cache worker for " + cacheDisplayName);
            cacheUpdateExecutor.execute(cacheAccessWorker);
        }
        return cacheAccessWorker;
    }

    @Override
    public void open() {
        lock.lock();
        try {
            if (open) {
                throw new IllegalStateException("Cache is already open.");
            }
            takeOwnershipNow();
            try {
                crossProcessCacheAccess.open();
                open = true;
            } finally {
                releaseOwnership();
            }
        } catch (Throwable throwable) {
            crossProcessCacheAccess.close();
            throw UncheckedException.throwAsUncheckedException(throwable);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public synchronized void close() {
        if (cacheAccessWorker != null) {
            cacheAccessWorker.stop();
            cacheAccessWorker = null;
        }
        if (cacheUpdateExecutor != null) {
            cacheUpdateExecutor.stop();
            cacheUpdateExecutor = null;
        }
        lock.lock();
        try {
            // Take ownership
            takeOwnershipNow();
            if (fileLockHeldByOwner != null) {
                fileLockHeldByOwner.run();
            }
            crossProcessCacheAccess.close();
            if (cacheClosedCount != 1) {
                LOG.debug("Cache {} was closed {} times.", cacheDisplayName, cacheClosedCount);
            }
        } finally {
            owner = null;
            lock.unlock();
        }
    }

    @Override
    public void useCache(Runnable action) {
        useCache(Factories.toFactory(action));
    }

    @Override
    public <T> T useCache(Factory<? extends T> factory) {
        if (lockOptions != null && lockOptions.getMode() == FileLockManager.LockMode.Shared) {
            throw new UnsupportedOperationException("Not implemented yet.");
        }

        boolean wasStarted;
        lock.lock();
        try {
            takeOwnership();
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

    /**
     * Waits until the current thread can take ownership.
     * Must be called while holding the lock.
     */
    private void takeOwnership() {
        while (owner != null && owner != Thread.currentThread()) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        owner = Thread.currentThread();
        operations.pushCacheAction();
    }

    /**
     * Takes ownership of the cache, asserting that this can be done without waiting.
     * Must be called while holding the lock.
     */
    private void takeOwnershipNow() {
        if (owner != null && owner != Thread.currentThread()) {
            throw new IllegalStateException(String.format("Cannot take ownership of %s as it is currently being used by another thread.", cacheDisplayName));
        }
        owner = Thread.currentThread();
        operations.pushCacheAction();
    }

    /**
     * Releases ownership of the cache.
     * Must be called while holding the lock.
     */
    private void releaseOwnership() {
        operations.popCacheAction();
        if (!operations.isInCacheAction()) {
            owner = null;
            condition.signalAll();
        }
    }

    @Override
    public <T> T longRunningOperation(Factory<? extends T> action) {
        boolean wasEnded = startLongRunningOperation();
        try {
            return action.create();
        } finally {
            finishLongRunningOperation(wasEnded);
        }
    }

    private boolean startLongRunningOperation() {
        boolean wasEnded;
        lock.lock();
        try {
            if (lockOptions.getMode() != None) {
                throw new UnsupportedOperationException("Long running operation not supported for this lock mode.");
            }
            if (operations.isInCacheAction()) {
                checkThreadIsOwner();
                wasEnded = onEndWork();
                owner = null;
                condition.signalAll();
            } else {
                wasEnded = false;
            }
            operations.pushLongRunningOperation();
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

    @Override
    public void longRunningOperation(Runnable action) {
        longRunningOperation(Factories.toFactory(action));
    }

    @Override
    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> newCache(final PersistentIndexedCacheParameters<K, V> parameters) {
        lock.lock();
        IndexedCacheEntry entry = caches.get(parameters.getCacheName());
        try {
            if (entry == null) {
                final File cacheFile = new File(baseDir, parameters.getCacheName() + ".bin");
                LOG.info("Creating new cache for {}, path {}, access {}", parameters.getCacheName(), cacheFile, this);
                Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = new Factory<BTreePersistentIndexedCache<K, V>>() {
                    public BTreePersistentIndexedCache<K, V> create() {
                        return doCreateCache(cacheFile, parameters.getKeySerializer(), parameters.getValueSerializer());
                    }
                };

                MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new DefaultMultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
                CacheDecorator decorator = parameters.getCacheDecorator();
                if (decorator != null) {
                    indexedCache = decorator.decorate(cacheFile.getAbsolutePath(), parameters.getCacheName(), indexedCache, crossProcessCacheAccess, getCacheAccessWorker());
                    if (fileLock == null) {
                        useCache(new Runnable() {
                            @Override
                            public void run() {
                                // Empty initial operation to trigger onStartWork calls
                            }
                        });
                    }
                }
                entry = new IndexedCacheEntry(parameters, indexedCache);
                caches.put(parameters.getCacheName(), entry);
                if (fileLock != null) {
                    indexedCache.afterLockAcquire(stateAtOpen);
                }
            } else {
                entry.assertCompatibleCacheParameters(parameters);
            }
        } finally {
            lock.unlock();
        }
        return entry.getCache();
    }

    @Override
    public synchronized void flush() {
        if(cacheAccessWorker != null) {
            cacheAccessWorker.flush();
        }
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return new BTreePersistentIndexedCache<K, V>(cacheFile, keySerializer, valueSerializer);
    }

    /**
     * Called just after the file lock has been acquire.
     */
    private void afterLockAcquire(FileLock fileLock) {
        assert this.fileLock == null;
        this.fileLock = fileLock;
        this.stateAtOpen = fileLock.getState();
        takeOwnershipNow();
        try {
            for (IndexedCacheEntry entry : caches.values()) {
                entry.getCache().afterLockAcquire(stateAtOpen);
            }
        } finally {
            releaseOwnership();
        }
        if (lockOptions.getMode() == None) {
            lockManager.allowContention(fileLock, whenContended());
        }
    }

    /**
     * Called just before the file lock is about to be released.
     */
    private void beforeLockRelease(FileLock fileLock) {
        assert this.fileLock == fileLock;
        try {
            cacheClosedCount++;
            takeOwnershipNow();
            try {
                // Notify caches that lock is to be released. The caches may do work on the cache files during this
                for (IndexedCacheEntry entry : caches.values()) {
                    entry.getCache().finishWork();
                }

                // Snapshot the state and notify the caches
                FileLock.State state = fileLock.getState();
                for (IndexedCacheEntry entry : caches.values()) {
                    entry.getCache().beforeLockRelease(state);
                }
            } finally {
                releaseOwnership();
            }
        } finally {
            this.fileLock = null;
            this.stateAtOpen = null;
            contended = false;
        }
    }

    private boolean onStartWork() {
        if (fileLockHeldByOwner != null) {
            return false;
        }
        fileLockHeldByOwner = crossProcessCacheAccess.acquireFileLock();
        return true;
    }

    private boolean onEndWork() {
        if (fileLockHeldByOwner == null) {
            return false;
        }
        if (contended) {
            try {
                fileLockHeldByOwner.run();
            } finally {
                fileLockHeldByOwner = null;
            }
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

                    takeOwnership();
                    try {
                        if (fileLockHeldByOwner != null) {
                            try {
                                fileLockHeldByOwner.run();
                            } finally {
                                fileLockHeldByOwner = null;
                            }
                        }
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

    private static class IndexedCacheEntry {
        private final MultiProcessSafePersistentIndexedCache cache;
        private final PersistentIndexedCacheParameters parameters;

        IndexedCacheEntry(PersistentIndexedCacheParameters parameters, MultiProcessSafePersistentIndexedCache cache) {
            this.parameters = parameters;
            this.cache = cache;
        }

        public MultiProcessSafePersistentIndexedCache getCache() {
            return cache;
        }

        public PersistentIndexedCacheParameters getParameters() {
            return parameters;
        }

        void assertCompatibleCacheParameters(PersistentIndexedCacheParameters parameters) {
            List<String> faultMessages = new ArrayList<String>();

            checkCacheNameMatch(faultMessages, parameters.getCacheName());
            checkCompatibleKeySerializer(faultMessages, parameters.getKeySerializer());
            checkCompatibleValueSerializer(faultMessages, parameters.getValueSerializer());
            checkCompatibleCacheDecorator(faultMessages, parameters.getCacheDecorator());

            if (!faultMessages.isEmpty()) {
                String lineSeparator = SystemProperties.getInstance().getLineSeparator();
                String faultMessage = CollectionUtils.join(lineSeparator, faultMessages);
                throw new InvalidCacheReuseException(
                    "The cache couldn't be reused because of the following mismatch:" + lineSeparator + faultMessage);
            }
        }

        private void checkCacheNameMatch(Collection<String> faultMessages, String cacheName) {
            if (!Objects.equal(cacheName, parameters.getCacheName())) {
                faultMessages.add(
                    String.format(" * Requested cache name (%s) doesn't match current cache name (%s)", cacheName,
                        parameters.getCacheName()));
            }
        }

        private void checkCompatibleKeySerializer(Collection<String> faultMessages, Serializer keySerializer) {
            if (!Objects.equal(keySerializer, parameters.getKeySerializer())) {
                faultMessages.add(
                    String.format(" * Requested key serializer type (%s) doesn't match current cache type (%s)",
                        keySerializer.getClass().getCanonicalName(),
                        parameters.getKeySerializer().getClass().getCanonicalName()));
            }
        }

        private void checkCompatibleValueSerializer(Collection<String> faultMessages, Serializer valueSerializer) {
            if (!Objects.equal(valueSerializer, parameters.getValueSerializer())) {
                faultMessages.add(
                    String.format(" * Requested value serializer type (%s) doesn't match current cache type (%s)",
                        valueSerializer.getClass().getCanonicalName(),
                        parameters.getValueSerializer().getClass().getCanonicalName()));
            }
        }

        private void checkCompatibleCacheDecorator(Collection<String> faultMessages, CacheDecorator cacheDecorator) {
            if (!Objects.equal(cacheDecorator, parameters.getCacheDecorator())) {
                faultMessages.add(
                    String.format(" * Requested cache decorator type (%s) doesn't match current cache type (%s)",
                        cacheDecorator, parameters.getCacheDecorator()));
            }
        }
    }

    private static class InvalidCacheReuseException extends GradleException {
        InvalidCacheReuseException(String message) {
            super(message);
        }
    }
}
