/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.locks.Lock;

class DefaultCrossProcessCacheAccess implements CrossProcessCacheAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCrossProcessCacheAccess.class);
    private final String cacheDisplayName;
    private final File lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final Lock stateLock;
    private final Action<FileLock> onOpen;
    private final Action<FileLock> onClose;
    private int lockCount;
    private FileLock fileLock;

    /**
     * Actions are notified when lock is opened or closed. Actions are called while holding state lock, so that no other threads are working with cache while these are running.
     *
     * @param stateLock Lock to hold while mutating state.
     * @param onOpen Action to run when the lock is opened. Action is called while holding state lock
     * @param onClose Action to run when the lock is closed. Action is called while holding state lock
     */
    public DefaultCrossProcessCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, FileLockManager lockManager, Lock stateLock, Action<FileLock> onOpen, Action<FileLock> onClose) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.lockManager = lockManager;
        this.stateLock = stateLock;
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    @Override
    public <T> T withFileLock(Factory<T> factory) {
        lock();
        try {
            return factory.create();
        } finally {
            unlock();
        }
    }

    protected void lock() {
        stateLock.lock();
        try {
            if (lockCount == 0) {
//                fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName);
                onOpen.execute(fileLock);
            }
            lockCount++;
            LOGGER.info("LOCK #" + lockCount);
        } finally {
            stateLock.unlock();
        }
    }

    protected void unlock() {
        stateLock.lock();
        try {
            lockCount--;
            if (lockCount == 0) {
                onClose.execute(fileLock);
//                fileLock.close();
                fileLock = null;
            }
            LOGGER.info("UNLOCK #" + lockCount);
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public Runnable acquireFileLock() {
        lock();
        return new Runnable() {
            @Override
            public void run() {
                unlock();
            }
        };
    }

    @Override
    public Runnable acquireFileLock(final Runnable completion) {
        lock();
        return new Runnable() {
            @Override
            public void run() {
                unlock();
                completion.run();
            }
        };
    }
}
