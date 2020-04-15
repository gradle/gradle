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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.cache.AsyncCacheAccess;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.FileAccess;
import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.InsufficientLockModeException;
import org.gradle.cache.LockOptions;
import org.gradle.cache.LockTimeoutException;
import org.gradle.cache.MultiProcessSafePersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack;
import org.gradle.internal.Cast;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;

@ThreadSafe
public class DefaultCacheAccess implements CacheCoordinator {
    private final static Logger LOG = LoggerFactory.getLogger(DefaultCacheAccess.class);
    private final static Runnable NO_OP = () -> {
        // Empty initial operation to trigger onStartWork calls
    };

    private final String cacheDisplayName;
    private final File baseDir;
    private final CacheCleanupAction cleanupAction;
    private final ExecutorFactory executorFactory;
    private final FileAccess fileAccess;
    private final Map<String, IndexedCacheEntry<?, ?>> caches = new HashMap<String, IndexedCacheEntry<?, ?>>();
    private final AbstractCrossProcessCacheAccess crossProcessCacheAccess;
    private final CacheAccessOperationsStack operations;

    private ManagedExecutor cacheUpdateExecutor;
    private CacheAccessWorker cacheAccessWorker;
    private final Lock stateLock = new ReentrantLock(); // protects the following state
    private final Condition condition = stateLock.newCondition();

    private boolean open;
    private Thread owner;
    private FileLock fileLock;
    private FileLock.State stateAtOpen;
    private Runnable fileLockHeldByOwner;
    private int cacheClosedCount;

    public DefaultCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, File baseDir, FileLockManager lockManager, CacheInitializationAction initializationAction, CacheCleanupAction cleanupAction, ExecutorFactory executorFactory) {
        this.cacheDisplayName = cacheDisplayName;
        this.baseDir = baseDir;
        this.cleanupAction = cleanupAction;
        this.executorFactory = executorFactory;
        this.operations = new CacheAccessOperationsStack();

        Action<FileLock> onFileLockAcquireAction = this::afterLockAcquire;
        Action<FileLock> onFileLockReleaseAction = this::beforeLockRelease;

        switch (lockOptions.getMode()) {
            case Shared:
                crossProcessCacheAccess = new FixedSharedModeCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions, lockManager, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                fileAccess = new UnitOfWorkFileAccess();
                break;
            case Exclusive:
                crossProcessCacheAccess = new FixedExclusiveModeCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions, lockManager, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                fileAccess = new UnitOfWorkFileAccess();
                break;
            case OnDemand:
                crossProcessCacheAccess = new LockOnDemandCrossProcessCacheAccess(cacheDisplayName, lockTarget, lockOptions.withMode(Exclusive), lockManager, stateLock, initializationAction, onFileLockAcquireAction, onFileLockReleaseAction);
                fileAccess = new UnitOfWorkFileAccess();
                break;
            case None:
                crossProcessCacheAccess = new NoLockingCacheAccess(this::notifyFinish);
                fileAccess = TransparentFileAccess.INSTANCE;
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
        stateLock.lock();
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
            stateLock.unlock();
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
        stateLock.lock();
        try {
            // Take ownership
            takeOwnershipNow();
            if (fileLockHeldByOwner != null) {
                fileLockHeldByOwner.run();
            }
            crossProcessCacheAccess.close();
            if (cleanupAction != null) {
                try {
                    if (cleanupAction.requiresCleanup()) {
                        cleanupAction.cleanup();
                    }
                } catch (Exception e) {
                    LOG.debug("Cache {} could not run cleanup action {}", cacheDisplayName, cleanupAction);
                }
            }
            if (cacheClosedCount != 1) {
                LOG.debug("Cache {} was closed {} times.", cacheDisplayName, cacheClosedCount);
            }
        } finally {
            owner = null;
            fileLockHeldByOwner = null;
            stateLock.unlock();
        }
    }

    @Override
    public <T> T withFileLock(Factory<? extends T> action) {
        return crossProcessCacheAccess.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        crossProcessCacheAccess.withFileLock(Factories.toFactory(action));
    }

    @Override
    public void useCache(Runnable action) {
        useCache(Factories.toFactory(action));
    }

    @Override
    public <T> T useCache(Factory<? extends T> factory) {
            boolean wasStarted;
            stateLock.lock();
            try {
                takeOwnership();
                try {
                    wasStarted = onStartWork();
                } catch (Throwable t) {
                    releaseOwnership();
                    throw UncheckedException.throwAsUncheckedException(t);
                }
            } finally {
                stateLock.unlock();
            }
            try {
                return factory.create();
            } finally {
                stateLock.lock();
                try {
                    try {
                        if (wasStarted) {
                            onEndWork();
                        }
                    } finally {
                        releaseOwnership();
                    }
                } finally {
                    stateLock.unlock();
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
    public <K, V> MultiProcessSafePersistentIndexedCache<K, V> newCache(final PersistentIndexedCacheParameters<K, V> parameters) {
        stateLock.lock();
        IndexedCacheEntry<K, V> entry = Cast.uncheckedCast(caches.get(parameters.getCacheName()));
        try {
            if (entry == null) {
                File cacheFile = findCacheFile(parameters);
                LOG.debug("Creating new cache for {}, path {}, access {}", parameters.getCacheName(), cacheFile, this);
                Factory<BTreePersistentIndexedCache<K, V>> indexedCacheFactory = () -> doCreateCache(cacheFile, parameters.getKeySerializer(), parameters.getValueSerializer());

                MultiProcessSafePersistentIndexedCache<K, V> indexedCache = new DefaultMultiProcessSafePersistentIndexedCache<K, V>(indexedCacheFactory, fileAccess);
                CacheDecorator decorator = parameters.getCacheDecorator();
                if (decorator != null) {
                    indexedCache = decorator.decorate(cacheFile.getAbsolutePath(), parameters.getCacheName(), indexedCache, crossProcessCacheAccess, getCacheAccessWorker());
                    if (fileLock == null) {
                        useCache(NO_OP);
                    }
                }
                entry = new IndexedCacheEntry<>(parameters, indexedCache);
                caches.put(parameters.getCacheName(), entry);
                if (fileLock != null) {
                    indexedCache.afterLockAcquire(stateAtOpen);
                }
            } else {
                entry.assertCompatibleCacheParameters(parameters);
            }
            return entry.getCache();
        } finally {
            stateLock.unlock();
        }
    }

    private <K, V> File findCacheFile(PersistentIndexedCacheParameters<K, V> parameters) {
        return new File(baseDir, parameters.getCacheName() + ".bin");
    }

    @Override
    public <K, V> boolean cacheExists(PersistentIndexedCacheParameters<K, V> parameters) {
        return findCacheFile(parameters).exists();
    }

    <K, V> BTreePersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return new BTreePersistentIndexedCache<>(cacheFile, keySerializer, valueSerializer);
    }

    /**
     * Called just after the file lock has been acquired.
     */
    private void afterLockAcquire(FileLock fileLock) {
        assert this.fileLock == null;
        this.fileLock = fileLock;
        this.stateAtOpen = fileLock.getState();
        takeOwnershipNow();
        try {
            for (IndexedCacheEntry<?, ?> entry : caches.values()) {
                entry.getCache().afterLockAcquire(stateAtOpen);
            }
        } finally {
            releaseOwnership();
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
                notifyFinish();

                // Snapshot the state and notify the caches
                FileLock.State state = fileLock.getState();
                for (IndexedCacheEntry<?, ?> entry : caches.values()) {
                    entry.getCache().beforeLockRelease(state);
                }
            } finally {
                releaseOwnership();
            }
        } finally {
            this.fileLock = null;
            this.stateAtOpen = null;
        }
    }

    private void notifyFinish() {
        // Notify caches that lock is to be released. The caches may do work on the cache files during this
        for (IndexedCacheEntry<?, ?> entry : caches.values()) {
            entry.getCache().finishWork();
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
        try {
            fileLockHeldByOwner.run();
        } finally {
            fileLockHeldByOwner = null;
        }
        return true;
    }

    private FileLock getFileLock() {
        stateLock.lock();
        try {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException(String.format("The %s has not been locked for this thread. File lock: %s, owner: %s", cacheDisplayName, fileLock != null, owner));
            }
        } finally {
            stateLock.unlock();
        }
        return fileLock;
    }

    private static class TransparentFileAccess implements FileAccess {
        private static final FileAccess INSTANCE = new TransparentFileAccess();

        @Override
        public <T> T readFile(Callable<? extends T> action) throws LockTimeoutException, FileIntegrityViolationException, InsufficientLockModeException {
            try {
                return action.call();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public <T> T readFile(Factory<? extends T> action) throws LockTimeoutException, FileIntegrityViolationException, InsufficientLockModeException {
            return action.create();
        }

        @Override
        public void updateFile(Runnable action) throws LockTimeoutException, FileIntegrityViolationException, InsufficientLockModeException {
            action.run();
        }

        @Override
        public void writeFile(Runnable action) throws LockTimeoutException, InsufficientLockModeException {
            action.run();
        }
    }

    private class UnitOfWorkFileAccess extends AbstractFileAccess {
        @Override
        public String toString() {
            return cacheDisplayName;
        }

        @Override
        public <T> T readFile(Factory<? extends T> action) throws LockTimeoutException {
            return getFileLock().readFile(action);
        }

        @Override
        public void updateFile(Runnable action) throws LockTimeoutException {
            getFileLock().updateFile(action);
        }

        @Override
        public void writeFile(Runnable action) throws LockTimeoutException {
            getFileLock().writeFile(action);
        }
    }

    Thread getOwner() {
        return owner;
    }

    FileAccess getFileAccess() {
        return fileAccess;
    }

    private static class IndexedCacheEntry<K, V> {
        private final MultiProcessSafePersistentIndexedCache<K, V> cache;
        private final PersistentIndexedCacheParameters<K, V> parameters;

        IndexedCacheEntry(PersistentIndexedCacheParameters<K, V> parameters, MultiProcessSafePersistentIndexedCache<K, V> cache) {
            this.parameters = parameters;
            this.cache = cache;
        }

        public MultiProcessSafePersistentIndexedCache<K, V> getCache() {
            return cache;
        }

        public PersistentIndexedCacheParameters<K, V> getParameters() {
            return parameters;
        }

        void assertCompatibleCacheParameters(PersistentIndexedCacheParameters<K, V> parameters) {
            List<String> faultMessages = new ArrayList<>();

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

        private void checkCompatibleKeySerializer(Collection<String> faultMessages, Serializer<K> keySerializer) {
            if (!Objects.equal(keySerializer, parameters.getKeySerializer())) {
                faultMessages.add(
                    String.format(" * Requested key serializer type (%s) doesn't match current cache type (%s)",
                        keySerializer.getClass().getCanonicalName(),
                        parameters.getKeySerializer().getClass().getCanonicalName()));
            }
        }

        private void checkCompatibleValueSerializer(Collection<String> faultMessages, Serializer<V> valueSerializer) {
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

    @VisibleForTesting
    static class InvalidCacheReuseException extends GradleException {
        InvalidCacheReuseException(String message) {
            super(message);
        }
    }
}
