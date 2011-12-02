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

import org.gradle.api.internal.Factory;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.UnitOfWorkCacheManager;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultCacheLockingManager implements CacheLockingManager {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final UnitOfWorkCacheManager cacheManager;
    private Thread owner;
    private String operationDisplayName;
    private boolean started;
    private int depth;

    public DefaultCacheLockingManager(FileLockManager fileLockManager, ArtifactCacheMetaData metaData) {
        this.cacheManager = new UnitOfWorkCacheManager(String.format("artifact cache '%s'", metaData.getCacheDir()), metaData.getCacheDir(), fileLockManager);
    }

    public void longRunningOperation(String operationDisplayName, final Runnable action) {
        longRunningOperation(operationDisplayName, new Factory<Object>() {
            public Object create() {
                action.run();
                return null;
            }
        });
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        boolean wasStarted = lockCache(operationDisplayName);
        try {
            if (!wasStarted) {
                cacheManager.onStartWork(operationDisplayName);
            }
            try {
                return action.create();
            } finally {
                if (!wasStarted) {
                    cacheManager.onEndWork();
                }
            }
        } finally {
            unlockCache(wasStarted);
        }
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        boolean wasStarted = startLongRunningOperation();
        try {
            if (wasStarted) {
                cacheManager.onEndWork();
            }
            try {
                return action.create();
            } finally {
                if (wasStarted) {
                    cacheManager.onStartWork(this.operationDisplayName);
                }
            }
        } finally {
            finishLongRunningOperation(wasStarted);
        }
    }

    private boolean startLongRunningOperation() {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException("Cannot start long running operation, as the artifact cache has not been locked.");
            }
            boolean wasStarted = started;
            started = false;
            return wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private void finishLongRunningOperation(boolean wasStarted) {
        lock.lock();
        try {
            started = wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private boolean lockCache(String operationDisplayName) {
        lock.lock();
        try {
            if (owner != Thread.currentThread()) {
                while (owner != null) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.asUncheckedException(e);
                    }
                }
                this.operationDisplayName = operationDisplayName;
                owner = Thread.currentThread();
            }
            boolean wasStarted = started;
            started = true;
            depth++;
            return wasStarted;
        } finally {
            lock.unlock();
        }
    }

    private void unlockCache(boolean wasStarted) {
        lock.lock();
        try {
            depth--;
            if (!wasStarted) {
                started = false;
                if (depth <= 0) {
                    owner = null;
                    operationDisplayName = null;
                    condition.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        return cacheManager.newCache(cacheFile, keyType, valueType);
    }
}
