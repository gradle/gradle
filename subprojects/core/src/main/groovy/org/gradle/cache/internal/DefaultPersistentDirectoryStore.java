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

import org.gradle.api.Action;
import org.gradle.cache.*;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;

public class DefaultPersistentDirectoryStore implements ReferencablePersistentCache {
    private final File dir;
    private final FileLockManager.LockMode lockMode;
    private final FileLockManager lockManager;
    private final String displayName;
    private DefaultCacheAccess cacheAccess;

    public DefaultPersistentDirectoryStore(File dir, String displayName, FileLockManager.LockMode lockMode, FileLockManager fileLockManager) {
        this.dir = dir;
        this.lockMode = lockMode;
        this.lockManager = fileLockManager;
        this.displayName = displayName != null ? String.format("%s (%s)", displayName, dir) : String.format("cache directory %s (%s)", dir.getName(), dir);
    }

    public DefaultPersistentDirectoryStore open() {
        GFileUtils.createDirectory(dir);
        cacheAccess = createCacheAccess();
        try {
            cacheAccess.open(lockMode);
            try {
                init();
            } catch (Throwable throwable) {
                if (cacheAccess != null) {
                    cacheAccess.close();
                }
                throw throwable;
            }
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }

        return this;
    }

    private DefaultCacheAccess createCacheAccess() {
        return new DefaultCacheAccess(displayName, getLockTarget(), lockManager);
    }

    protected void withExclusiveLock(Action<FileLock> action) {
        if (cacheAccess != null && (cacheAccess.getFileLock().getMode() == FileLockManager.LockMode.Exclusive)) {
            action.execute(getLock());
        } else {
            boolean reopen = cacheAccess != null;
            close();
            DefaultCacheAccess exclusiveAccess = createCacheAccess();
            exclusiveAccess.open(FileLockManager.LockMode.Exclusive);
            try {
                action.execute(exclusiveAccess.getFileLock());
            } finally {
                exclusiveAccess.close();
            }
            if (reopen) {
                cacheAccess = createCacheAccess();
                cacheAccess.open(lockMode);
            }
        }
    }


    protected File getLockTarget() {
        return dir;
    }

    protected void init() throws IOException {
    }

    public void close() {
        if (cacheAccess != null) {
            try {
                cacheAccess.close();
            } finally {
                cacheAccess = null;
            }
        }

    }

    public FileLock getLock() {
        return cacheAccess.getFileLock();
    }

    public File getBaseDir() {
        return dir;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Class<V> valueType) {
        return cacheAccess.newCache(cacheFile, keyType, valueType);
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(File cacheFile, Class<K> keyType, Serializer<V> valueSerializer) {
        return cacheAccess.newCache(cacheFile, keyType, valueSerializer);
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return cacheAccess.useCache(operationDisplayName, action);
    }

    public void useCache(String operationDisplayName, Runnable action) {
        cacheAccess.useCache(operationDisplayName, action);
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return cacheAccess.longRunningOperation(operationDisplayName, action);
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        cacheAccess.longRunningOperation(operationDisplayName, action);
    }
}
