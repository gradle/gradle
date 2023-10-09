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
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FileLockReleasedSignal;
import org.gradle.cache.LockOptions;
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.locks.Lock;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;
import static org.gradle.cache.FileLockManager.LockMode.Shared;

public class LockOnDemandCrossProcessCacheAccess extends AbstractCrossProcessCacheAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(LockOnDemandCrossProcessCacheAccess.class);
    private final String cacheDisplayName;
    private final File lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final Lock stateLock;
    private final Action<FileLock> onOpen;
    private final Action<FileLock> onClose;
    private final Runnable unlocker;
    private final Action<FileLockReleasedSignal> whenContended;
    private int lockCount;
    private FileLock fileLock;
    private CacheInitializationAction initAction;
    private FileLockReleasedSignal lockReleaseSignal;

    /**
     * Actions are notified when lock is opened or closed. Actions are called while holding state lock, so that no other threads are working with cache while these are running.
     *
     * @param stateLock Lock to hold while mutating state.
     * @param onOpen Action to run when the lock is opened. Action is called while holding state lock
     * @param onClose Action to run when the lock is closed. Action is called while holding state lock
     */
    public LockOnDemandCrossProcessCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, FileLockManager lockManager, Lock stateLock, CacheInitializationAction initAction, Action<FileLock> onOpen, Action<FileLock> onClose) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.lockManager = lockManager;
        this.stateLock = stateLock;
        this.initAction = initAction;
        this.onOpen = onOpen;
        this.onClose = onClose;
        unlocker = new UnlockAction();
        whenContended = new ContendedAction();
    }

    @Override
    public void open() {
        // Don't need to do anything
    }

    @Override
    public void close() {
        stateLock.lock();
        try {
            if (lockCount != 0) {
                throw new IllegalStateException(String.format("Cannot close cache access for %s as it is currently in use for %s operations.", cacheDisplayName, lockCount));
            }
            releaseLockIfHeld();
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public <T> T withFileLock(Factory<T> factory) {
        incrementLockCount();
        try {
            return factory.create();
        } finally {
            decrementLockCount();
        }
    }

    private void incrementLockCount() {
        stateLock.lock();
        try {
            if (fileLock == null) {
                if (lockCount != 0) {
                    throw new IllegalStateException("Mismatched lock count.");
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Acquiring file lock for {}", cacheDisplayName);
                }
                fileLock = getFileLock(lockOptions);
            }
            lockCount++;
        } finally {
            stateLock.unlock();
        }
    }

    private FileLock getFileLock(LockOptions lockOptions) {
        if (lockOptions.getMode() == Exclusive) {
            return FixedExclusiveModeCrossProcessCacheAccess.getFileLock(lockManager, lockTarget, lockOptions, cacheDisplayName, initAction, onOpen, whenContended);
        } else if (lockOptions.getMode() == Shared) {
            return FixedSharedModeCrossProcessCacheAccess.getFileLock(lockManager, lockTarget, lockOptions, cacheDisplayName, initAction, onOpen, whenContended);
        } else {
            throw new UnsupportedOperationException("Unsupported lock mode with on demand locking: " + lockOptions.getMode());
        }
    }

    private void decrementLockCount() {
        stateLock.lock();
        try {
            if (lockCount <= 0 || fileLock == null) {
                throw new IllegalStateException("Mismatched lock count.");
            }
            lockCount--;
            if (lockCount == 0 && lockReleaseSignal != null) {
                releaseLockIfHeld();
            } // otherwise, keep lock open
        } finally {
            stateLock.unlock();
        }
    }

    private void releaseLockIfHeld() {
        if (fileLock == null) {
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Releasing file lock for {}", cacheDisplayName);
        }
        try {
            onClose.execute(fileLock);
        } finally {
            try {
                fileLock.close();
                fileLock = null;
            } finally {
                if (lockReleaseSignal != null) {
                    lockReleaseSignal.trigger();
                    lockReleaseSignal = null;
                }
            }
        }
    }

    @Override
    public Runnable acquireFileLock() {
        incrementLockCount();
        return unlocker;
    }

    private class ContendedAction implements Action<FileLockReleasedSignal> {
        @Override
        public void execute(FileLockReleasedSignal signal) {
            stateLock.lock();
            try {
                if (lockCount == 0) {
                    LOGGER.debug("Lock on {} requested by another process - releasing lock.", cacheDisplayName);
                    releaseLockIfHeld();
                    signal.trigger();
                } else {
                    // Lock is in use - mark as contended
                    LOGGER.debug("Lock on {} requested by another process - lock is in use and will be released when operation completed.", cacheDisplayName);
                    lockReleaseSignal = signal;
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    private class UnlockAction implements Runnable {
        @Override
        public void run() {
            decrementLockCount();
        }
    }
}
