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

import org.gradle.cache.CacheOpenException;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;

import java.io.File;

import static org.gradle.cache.internal.FileLockManager.LockMode.Exclusive;

/**
 * A {@link CrossProcessCacheAccess} implementation used when a cache is opened with a shared lock that is held until the cache is closed. The contract for {@link CrossProcessCacheAccess} requires an
 * exclusive lock be held, so this implementation simply throws 'not supported' exceptions.
 */
public class FixedSharedModeCrossProcessCacheAccess extends AbstractCrossProcessCacheAccess {
    private final String cacheDisplayName;
    private final File lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private FileLock fileLock;

    public FixedSharedModeCrossProcessCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, FileLockManager lockManager) {
        this.cacheDisplayName = cacheDisplayName;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.lockManager = lockManager;
    }

    @Override
    public void open(final CacheInitializationAction initializationAction) {
        if (fileLock != null) {
            throw new IllegalStateException("File lock " + lockTarget + " is already open.");
        }
        fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName);

        boolean rebuild = initializationAction.requiresInitialization(fileLock);
        if (rebuild) {
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

    @Override
    public FileLock getLock() throws IllegalStateException {
        return fileLock;
    }

    @Override
    public void close() {
        if (fileLock != null) {
            try {
                fileLock.close();
            } finally {
                fileLock = null;
            }
        }
    }

    @Override
    public Runnable acquireFileLock() {
        throw failure();
    }

    @Override
    public <T> T withFileLock(Factory<T> factory) {
        throw failure();
    }

    @Override
    public Runnable acquireFileLock(Runnable completion) {
        throw failure();
    }

    protected UnsupportedOperationException failure() {
        return new UnsupportedOperationException("Cannot escalate a shared lock to an exclusive lock. This is not yet supported.");
    }
}
