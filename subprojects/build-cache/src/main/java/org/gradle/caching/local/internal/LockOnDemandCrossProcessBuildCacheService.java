/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal;

import com.google.common.io.Closer;
import org.gradle.api.Action;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.HasCleanupAction;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.AbstractCrossProcessCacheAccess;
import org.gradle.cache.internal.LockOnDemandCrossProcessCacheAccess;
import org.gradle.cache.internal.cacheops.CacheAccessOperationsStack;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.internal.CacheInitializationAction.NO_INIT_REQUIRED;

public class LockOnDemandCrossProcessBuildCacheService implements NextGenBuildCacheService, HasCleanupAction {
    private final static Logger LOG = LoggerFactory.getLogger(LockOnDemandCrossProcessBuildCacheService.class);

    private final String cacheDisplayName;
    private final StatefulNextGenBuildCacheService delegate;
    private final AbstractCrossProcessCacheAccess crossProcessCacheAccess;
    private final CacheAccessOperationsStack operations;

    private final Lock stateLock = new ReentrantLock(); // protects the following state
    private final Condition condition = stateLock.newCondition();
    private final PersistentCache persistentCache;

    private Thread owner;
    private FileLock fileLock;
    private Runnable fileLockHeldByOwner;
    private int cacheClosedCount;

    public LockOnDemandCrossProcessBuildCacheService(
        String cacheDisplayName,
        File lockTarget,
        FileLockManager lockManager,
        StatefulNextGenBuildCacheService delegate,
        Function<HasCleanupAction, PersistentCache> persistentCacheFactory
    ) {
        this.cacheDisplayName = cacheDisplayName;
        this.delegate = delegate;
        this.operations = new CacheAccessOperationsStack();

        Action<FileLock> onFileLockAcquireAction = this::afterLockAcquire;
        Action<FileLock> onFileLockReleaseAction = this::beforeLockRelease;

        LockOptionsBuilder lockOptions = LockOptionsBuilder
            .mode(Exclusive)
            // TODO Is cross-version something we need?
            .useCrossVersionImplementation();
        this.crossProcessCacheAccess = new LockOnDemandCrossProcessCacheAccess(
            cacheDisplayName,
            lockTarget,
            lockOptions,
            lockManager,
            stateLock,
            NO_INIT_REQUIRED,
            onFileLockAcquireAction,
            onFileLockReleaseAction);
        this.persistentCache = persistentCacheFactory.apply(this);

        withOwnershipNow(() -> {
            try {
                crossProcessCacheAccess.open();
            } catch (Throwable throwable) {
                crossProcessCacheAccess.close();
                throw UncheckedException.throwAsUncheckedException(throwable);
            }
        });
    }

    @Override
    public boolean contains(BuildCacheKey key) {
        return crossProcessCacheAccess.withFileLock(() -> delegate.contains(key));
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter legacyWriter) throws BuildCacheException {
        crossProcessCacheAccess.withFileLock(() -> {
            delegate.store(key, legacyWriter);
            return null;
        });
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        crossProcessCacheAccess.withFileLock(() -> {
            delegate.store(key, writer);
            return null;
        });
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return crossProcessCacheAccess.withFileLock(() -> delegate.load(key, reader));
    }

    @Override
    public void cleanup() {
        crossProcessCacheAccess.withFileLock(() -> {
            delegate.cleanup();
            return null;
        });
    }

    @Override
    public synchronized void close() {
        withOwnershipNow(() -> {
            try {
                if (fileLockHeldByOwner != null) {
                    fileLockHeldByOwner.run();
                }

                Closer closer = Closer.create();
                closer.register(crossProcessCacheAccess);
                closer.register(persistentCache);
                closer.close();

                if (cacheClosedCount != 1) {
                    LOG.debug("Cache {} was closed {} times.", cacheDisplayName, cacheClosedCount);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                owner = null;
                fileLockHeldByOwner = null;
            }
        });
    }

    private void withOwnershipNow(Runnable action) {
        stateLock.lock();
        try {
            // Take ownership
            takeOwnershipNow();
            try {
                action.run();
            } finally {
                releaseOwnership();
            }
        } finally {
            stateLock.unlock();
        }
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

    /**
     * Called just after the file lock has been acquired.
     */
    private void afterLockAcquire(FileLock fileLock) {
        assert this.fileLock == null;
        this.fileLock = fileLock;

        withOwnershipNow(delegate::open);
    }

    /**
     * Called just before the file lock is about to be released.
     */
    private void beforeLockRelease(FileLock fileLock) {
        assert this.fileLock == fileLock;
        try {
            cacheClosedCount++;
            withOwnershipNow(delegate::close);
        } finally {
            this.fileLock = null;
        }
    }
}
