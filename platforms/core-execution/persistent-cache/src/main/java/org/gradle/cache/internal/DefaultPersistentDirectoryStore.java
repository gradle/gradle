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

import org.apache.commons.io.FileUtils;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.LockOptions;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

import static org.gradle.cache.internal.CacheInitializationAction.NO_INIT_REQUIRED;

public class DefaultPersistentDirectoryStore implements ReferencablePersistentCache {

    public static final int CLEANUP_INTERVAL_IN_HOURS = 24;

    private final File dir;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final String displayName;

    protected final File propertiesFile;
    private final File gcFile;

    private final DefaultCacheCleanupExecutor cleanupExecutor;
    private DefaultCacheCoordinator cacheAccess;

    public DefaultPersistentDirectoryStore(
        File dir,
        @Nullable String displayName,
        LockOptions lockOptions,
        CacheCleanupStrategy cacheCleanupStrategy,
        FileLockManager fileLockManager,
        ExecutorFactory executorFactory,
        BuildOperationRunner buildOperationRunner
    ) {
        this.dir = dir;
        this.lockOptions = lockOptions;
        this.lockManager = fileLockManager;
        this.executorFactory = executorFactory;
        this.propertiesFile = new File(dir, "cache.properties");
        this.gcFile = new File(dir, "gc.properties");
        this.displayName = displayName != null ? (displayName + " (" + dir + ")") : ("cache directory " + dir.getName() + " (" + dir + ")");
        this.cleanupExecutor = new DefaultCacheCleanupExecutor(this, gcFile, cacheCleanupStrategy, buildOperationRunner);
    }

    @Override
    public DefaultPersistentDirectoryStore open() {
        try {
            FileUtils.forceMkdir(dir);
            cacheAccess = createCacheAccess();
            cacheAccess.open();
        } catch (Throwable e) {
            throw new CacheOpenException(String.format("Could not open %s.", this), e);
        }

        return this;
    }

    private DefaultCacheCoordinator createCacheAccess() {
        return new DefaultCacheCoordinator(displayName, getLockTarget(), lockOptions, dir, lockManager, getInitAction(), cleanupExecutor, executorFactory);
    }

    private File getLockTarget() {
        return dir;
    }

    protected CacheInitializationAction getInitAction() {
        return NO_INIT_REQUIRED;
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
    public Collection<File> getReservedCacheFiles() {
        return Arrays.asList(propertiesFile, gcFile, determineLockTargetFile(getLockTarget()));
    }

    static File determineLockTargetFile(File target) {
        return new File(target, target.getName() + ".lock");
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public <K, V> IndexedCache<K, V> createIndexedCache(IndexedCacheParameters<K, V> parameters) {
        return cacheAccess.newCache(parameters);
    }

    @Override
    public <K, V> IndexedCache<K, V> createIndexedCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
        return cacheAccess.newCache(IndexedCacheParameters.of(name, keyType, valueSerializer));
    }

    @Override
    public <K, V> boolean indexedCacheExists(IndexedCacheParameters<K, V> parameters) {
        return cacheAccess.cacheExists(parameters);
    }

    @Override
    public <T> T withFileLock(Supplier<? extends T> action) {
        return cacheAccess.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cacheAccess.withFileLock(action);
    }

    @Override
    public <T> T useCache(Supplier<? extends T> action) {
        return cacheAccess.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cacheAccess.useCache(action);
    }

    @Override
    public void cleanup() {
        cacheAccess.cleanup();
    }

}
