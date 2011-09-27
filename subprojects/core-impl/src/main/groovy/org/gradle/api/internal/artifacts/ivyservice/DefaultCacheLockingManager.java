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

import org.apache.ivy.core.settings.IvySettings;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.util.GFileUtils;
import org.gradle.util.UncheckedException;
import org.jfrog.wharf.ivy.lock.LockHolder;
import org.jfrog.wharf.ivy.lock.LockHolderFactory;
import org.jfrog.wharf.ivy.lock.LockLogger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultCacheLockingManager implements LockHolderFactory, CacheLockingManager {
    private final FileLockManager fileLockManager;
    private final Lock lock = new ReentrantLock();
    private final Map<File, ArtifactLock> locks = new HashMap<File, ArtifactLock>();
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
            if (!locks.isEmpty()) {
                new CompositeStoppable().addCloseables(locks.values()).stop();
                throw new IllegalStateException("Some artifact file locks were not released.");
            }
        } finally {
            locked = false;
            locks.clear();
            lock.unlock();
        }
    }

    public LockLogger getLogger() {
        throw new UnsupportedOperationException();
    }

    public long getTimeoutInMs() {
        throw new UnsupportedOperationException();
    }

    public long getSleepTimeInMs() {
        throw new UnsupportedOperationException();
    }

    public String getLockFileSuffix() {
        throw new UnsupportedOperationException();
    }

    private void acquire(File protectedFile) {
        lock.lock();
        try {
            if (!locked) {
                throw new IllegalStateException("Cannot acquire artifact lock, as the artifact cache is not locked by this process.");
            }
            ArtifactLock artifactLock = locks.get(protectedFile);
            if (artifactLock == null) {
                FileLock fileLock = fileLockManager.lock(protectedFile, FileLockManager.LockMode.Exclusive, String.format("artifact file %s", protectedFile), operationDisplayName);
                artifactLock = new ArtifactLock(fileLock);
                locks.put(protectedFile, artifactLock);
            }
            artifactLock.refCount++;
        } finally {
            lock.unlock();
        }
    }

    private void release(File protectedFile) {
        lock.lock();
        try {
            ArtifactLock artifactLock = locks.get(protectedFile);
            if (artifactLock == null || artifactLock.refCount <= 0) {
                throw new IllegalStateException("Cannot release artifact file lock, as the file is not locked.");
            }
            artifactLock.refCount--;
            if (artifactLock.refCount == 0) {
                locks.remove(protectedFile);
                artifactLock.lock.close();
            }
        } finally {
            lock.unlock();
        }
    }

    public LockHolder getLockHolder(final File protectedFile) {
        final File canonicalFile = GFileUtils.canonicalise(protectedFile);
        return new LockHolder() {
            public void releaseLock() {
                release(canonicalFile);
            }

            public boolean acquireLock() {
                acquire(canonicalFile);
                protectedFile.getParentFile().mkdirs();
                return true;
            }

            public File getLockFile() {
                throw new UnsupportedOperationException();
            }

            public File getProtectedFile() {
                return protectedFile;
            }

            public String stateMessage() {
                return "ok";
            }
        };
    }

    public LockHolder getOrCreateLockHolder(File protectedFile) {
        return getLockHolder(protectedFile);
    }

    public void close() throws IOException {
    }

    public void setSettings(IvySettings settings) {
        throw new UnsupportedOperationException();
    }

    private static class ArtifactLock implements Closeable {
        private final FileLock lock;
        private int refCount;

        private ArtifactLock(FileLock lock) {
            this.lock = lock;
        }

        public void close() {
            lock.close();
        }
    }
}
