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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.UnitOfWorkCacheManager;
import org.gradle.util.UncheckedException;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultCacheLockingManager implements CacheLockingManager {
    private final Lock lock = new ReentrantLock();
    private final UnitOfWorkCacheManager cacheManager;

    private boolean locked;
    private String operationDisplayName;

    public DefaultCacheLockingManager(FileLockManager fileLockManager, ArtifactCacheMetaData metaData) {
        this.cacheManager = new UnitOfWorkCacheManager(String.format("artifact cache '%s'", metaData.getCacheDir()), metaData.getCacheDir(), fileLockManager);
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
            cacheManager.onStartWork();
            locked = true;
        } finally {
            lock.unlock();
        }
    }

    private void unlockCache() {
        lock.lock();
        try {
            cacheManager.onEndWork();
        } finally {
            locked = false;
            lock.unlock();
        }
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        return cacheManager.newCache(cacheFile, keyType, valueType);
    }
}
