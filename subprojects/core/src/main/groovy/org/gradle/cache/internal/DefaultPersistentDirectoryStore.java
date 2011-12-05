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

import org.gradle.api.internal.Factory;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.io.IOException;

public class DefaultPersistentDirectoryStore implements PersistentCache {
    private final File dir;
    private final FileLockManager.LockMode lockMode;
    private final FileLockManager lockManager;
    private final String displayName;
    private FileLock lock;

    public DefaultPersistentDirectoryStore(File dir, String displayName, FileLockManager.LockMode lockMode, FileLockManager fileLockManager) {
        this.dir = dir;
        this.lockMode = lockMode;
        this.lockManager = fileLockManager;
        this.displayName = displayName != null ? displayName : String.format("cache directory %s", dir);
    }

    public DefaultPersistentDirectoryStore open() {
        dir.mkdirs();
        try {
            lock = lockManager.lock(getLockTarget(), lockMode, toString());
            try {
                init();
            } catch (Throwable throwable) {
                lock.close();
                lock = null;
                throw throwable;
            }
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }
        return this;
    }

    protected File getLockTarget() {
        return dir;
    }

    protected void init() throws IOException {
    }

    public void close() {
        if (lock != null) {
            try {
                lock.close();
            } finally {
                lock = null;
            }
        }
    }

    protected FileLock getLock() {
        return lock;
    }

    public File getBaseDir() {
        return dir;
    }
    @Override
    public String toString() {
        return displayName;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        throw new UnsupportedOperationException();
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        throw new UnsupportedOperationException();
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        throw new UnsupportedOperationException();
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        throw new UnsupportedOperationException();
    }
}
