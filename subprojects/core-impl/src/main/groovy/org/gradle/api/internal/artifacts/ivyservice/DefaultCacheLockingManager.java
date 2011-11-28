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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.LockTimeoutException;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultCacheLockingManager implements CacheLockingManager {
    private final FileLockManager fileLockManager;
    private final Lock lock = new ReentrantLock();
    private final Map<File, FileLock> metadataLocks = new HashMap<File, FileLock>();
    
    private boolean locked;
    private String operationDisplayName;

    public DefaultCacheLockingManager(FileLockManager fileLockManager) {
        this.fileLockManager = fileLockManager;
    }

    public <T> T withCacheLock(String operationDisplayName, Callable<? extends T> action) {
        lockCache(operationDisplayName);
        try {
            return action.call();
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        } finally {
            unlockCache();
        }
    }

    private void lockCache(String operationDisplayName) {
        lock.lock();
        try {
            if (locked) {
                throw new IllegalStateException("Cannot lock the artifact cache, as it is already locked by this process.");
            }
            this.operationDisplayName = operationDisplayName;
            locked = true;
        } finally {
            lock.unlock();
        }
    }

    private void unlockCache() {
        lock.lock();
        try {
            // Metadata locks are opened on demand, but closed when the cache lock is released
            closeMetadataLocks();
        } finally {
            locked = false;
            metadataLocks.clear();
            lock.unlock();
        }
    }

    private void closeMetadataLocks() {
        new CompositeStoppable().addCloseables(metadataLocks.values()).stop();
    }

    public FileLock getCacheMetadataFileLock(final File metadataFile) {
        return new MetadataFileLock(metadataFile);
    }

    private FileLock acquireMetadataFileLock(File metadataFile) {
        lock.lock();
        try {
            if (!locked) {
                throw new IllegalStateException("Cannot acquire artifact lock, as the artifact cache is not locked by this process.");
            }
            FileLock metadataFileLock = metadataLocks.get(metadataFile);
            if (metadataFileLock == null) {
                metadataFileLock = fileLockManager.lock(metadataFile, FileLockManager.LockMode.Exclusive, String.format("metadata file %s", metadataFile.getName()), operationDisplayName);
                metadataLocks.put(metadataFile, metadataFileLock);
            }
            return metadataFileLock;
        } finally {
            lock.unlock();
        }
    }

    /**
     * A FileLock implementation that locks on first use within a cache lock block, and retains file lock for the duration of the Cache lock.
     * Any call to {@link #readFromFile} or {@link #writeToFile} will open the lock, even if it was previously closed. Thus the lock can be used for a long
     * lived persistent cache, as long as all access occurs within a withCacheLock() block.
     */
    private class MetadataFileLock implements FileLock {
        private final File metadataFile;

        public MetadataFileLock(File metadataFile) {
            this.metadataFile = metadataFile;
        }

        public boolean getUnlockedCleanly() {
            // TODO Not sure about this
            return acquireLock().getUnlockedCleanly();
        }

        public boolean isLockFile(File file) {
            // TODO Not sure about this
            return acquireLock().isLockFile(file);
        }

        public <T> T readFromFile(Callable<T> action) throws LockTimeoutException {
            return acquireLock().readFromFile(action);
        }

        public void writeToFile(Runnable action) throws LockTimeoutException {
            acquireLock().writeToFile(action);
        }

        public void close() {
        }

        private FileLock acquireLock() {
            return acquireMetadataFileLock(metadataFile);
        }
    }
}
