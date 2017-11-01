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

import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.File;

public class DefaultPersistentDirectoryStore implements ReferencablePersistentCache {
    private final File dir;
    private final CacheBuilder.LockTarget lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final String displayName;
    protected final File propertiesFile;
    protected final File gcFile;
    private CacheCoordinator cacheAccess;

    public DefaultPersistentDirectoryStore(File dir, String displayName, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        this.dir = dir;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.lockManager = fileLockManager;
        this.executorFactory = executorFactory;
        this.propertiesFile = new File(dir, "cache.properties");
        this.gcFile = new File(dir, "gc.properties");
        this.displayName = displayName != null ? (displayName + " (" + dir + ")") : ("cache directory " + dir.getName() + " (" + dir + ")");
    }

    @Override
    public DefaultPersistentDirectoryStore open() {
        GFileUtils.mkdirs(dir);
        cacheAccess = createCacheAccess();
        try {
            cacheAccess.open();
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }

        return this;
    }

    private CacheCoordinator createCacheAccess() {
        return new DefaultCacheAccess(displayName, getLockTarget(), lockOptions, dir, lockManager, getInitAction(), getCleanupAction(), executorFactory);
    }

    private File getLockTarget() {
        switch (lockTarget) {
            case CacheDirectory:
            case DefaultTarget:
                return dir;
            case CachePropertiesFile:
                return propertiesFile;
            default:
                throw new IllegalArgumentException("Unsupported lock target: " + lockTarget);
        }
    }

    protected CacheInitializationAction getInitAction() {
        return new CacheInitializationAction() {
            public boolean requiresInitialization(FileLock fileLock) {
                return false;
            }

            public void initialize(FileLock fileLock) {
                throw new UnsupportedOperationException();
            }
        };
    }

    protected CacheCleanupAction getCleanupAction() {
        return new CacheCleanupAction() {
            @Override
            public boolean requiresCleanup() {
                return false;
            }

            @Override
            public void cleanup() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public void close() {
        if (cacheAccess != null) {
            try {
                cacheAccess.close();
            } finally {
                cacheAccess = null;
            }
        }
    }

    @Override
    public File getBaseDir() {
        return dir;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
        return cacheAccess.newCache(parameters);
    }

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
        return cacheAccess.newCache(new PersistentIndexedCacheParameters<K, V>(name, keyType, valueSerializer));
    }

    @Override
    public <T> T withFileLock(Factory<? extends T> action) {
        return cacheAccess.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cacheAccess.withFileLock(action);
    }

    @Override
    public <T> T useCache(Factory<? extends T> action) {
        return cacheAccess.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cacheAccess.useCache(action);
    }

}
