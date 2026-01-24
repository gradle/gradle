/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CleanableStore;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FineGrainedCacheCleanupStrategy;
import org.gradle.cache.FineGrainedPersistentCache;
import org.gradle.cache.HasCleanupAction;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.DefaultLockOptions;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.serialize.Serializer;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultCacheFactory implements CacheFactory, Closeable {
    private final Map<File, DirCacheReference<?>> dirCaches = new HashMap<>();
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final Lock lock = new ReentrantLock();

    public DefaultCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        this.lockManager = fileLockManager;
        this.executorFactory = executorFactory;
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    @Override
    public PersistentCache open(File cacheDir, String displayName, Map<String, ?> properties, LockOptions lockOptions, @Nullable Consumer<? super PersistentCache> initializer, CacheCleanupStrategy cacheCleanupStrategy) throws CacheOpenException {
        lock.lock();
        try {
            DirCacheReference<PersistentCache> dirCacheReference = openDirCacheReference(
                PersistentCache.class,
                cacheDir,
                properties,
                lockOptions,
                () -> openPersistentCache(cacheDir, displayName, properties, lockOptions, initializer, cacheCleanupStrategy)
            );
            return new ReferenceTrackingPersistentCache(dirCacheReference);
        } finally {
            lock.unlock();
        }
    }

    private PersistentCache openPersistentCache(File cacheDir, String displayName, Map<String, ?> properties, LockOptions lockOptions, @Nullable Consumer<? super PersistentCache> initializer, CacheCleanupStrategy cacheCleanupStrategy) {
        ReferencablePersistentCache cache;
        if (!properties.isEmpty() || initializer != null) {
            Consumer<? super PersistentCache> initAction = initializer != null ? initializer : __ -> {};
            cache = new DefaultPersistentDirectoryCache(cacheDir, displayName, properties, lockOptions, initAction, cacheCleanupStrategy, lockManager, executorFactory);
        } else {
            cache = new DefaultPersistentDirectoryStore(cacheDir, displayName, lockOptions, cacheCleanupStrategy, lockManager, executorFactory);
        }
        cache.open();
        return cache;
    }

    @Override
    public FineGrainedPersistentCache openFineGrained(File cacheDir, String displayName, FineGrainedCacheCleanupStrategy cacheCleanupStrategy) throws CacheOpenException {
        lock.lock();
        try {
            DirCacheReference<FineGrainedPersistentCache> dirCacheReference = openDirCacheReference(
                FineGrainedPersistentCache.class,
                cacheDir,
                Collections.emptyMap(),
                DefaultLockOptions.mode(FileLockManager.LockMode.Exclusive),
                () -> new DefaultFineGrainedPersistentCache(cacheDir, displayName, lockManager, cacheCleanupStrategy)
            );
            return new ReferenceTrackingFineGrainedCache(dirCacheReference);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void visitCaches(CacheVisitor visitor) {
        dirCaches.values().stream().map(dirCacheReference -> dirCacheReference.cache).forEach(visitor::visit);
    }

    @Override
    public void close() {
        lock.lock();
        try {
            CompositeStoppable.stoppable(dirCaches.values()).stop();
        } finally {
            dirCaches.clear();
            lock.unlock();
        }
    }

    private <T extends Closeable & CleanableStore & HasCleanupAction> DirCacheReference<T> openDirCacheReference(
        Class<T> cacheType,
        File cacheDir,
        Map<String, ?> properties,
        LockOptions lockOptions,
        Supplier<T> cacheFactory
    ) {
        DirCacheReference<?> dirCacheReference = dirCaches.get(cacheDir);
        if (dirCacheReference == null) {
            T cache = cacheFactory.get();
            dirCacheReference = new DirCacheReference<>(cache, properties, lockOptions);
            dirCaches.put(cacheDir, dirCacheReference);
        } else {
            dirCacheReference.validate(cacheType, cacheDir, properties, lockOptions);
        }
        return Cast.uncheckedCast(dirCacheReference);
    }

    private class DirCacheReference<T extends Closeable & CleanableStore & HasCleanupAction> implements Closeable {
        private final Map<String, ?> properties;
        private final LockOptions lockOptions;
        private final T cache;
        private final Set<T> references = new HashSet<>();

        DirCacheReference(T cache, Map<String, ?> properties, LockOptions lockOptions) {
            this.cache = cache;
            this.properties = properties;
            this.lockOptions = lockOptions;
            onOpen(cache);
        }

        public void addReference(T cache) {
            references.add(cache);
        }

        public void release(T cache) {
            lock.lock();
            try {
                if (references.remove(cache) && references.isEmpty()) {
                    close();
                }
            } finally {
                lock.unlock();
            }
        }

        public void validate(Class<?> cacheType, File cacheDir, Map<String, ?> properties, LockOptions lockOptions) {
            if (!cacheType.isAssignableFrom(this.cache.getClass())) {
                throw new IllegalStateException(String.format("Cache '%s' is already open as '%s' that is not a subtype of expected '%s'.", cacheDir, this.cache.getClass().getName(), cacheType.getName()));
            }
            if (!lockOptions.equals(this.lockOptions)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different lock options.", cacheDir));
            }
            if (!properties.equals(this.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different properties.", cacheDir));
            }
        }

        @Override
        public void close() {
            onClose(cache);
            dirCaches.values().remove(this);
            references.clear();
            CompositeStoppable.stoppable(cache).stop();
        }
    }

    private static class ReferenceTrackingPersistentCache implements PersistentCache {
        private final DirCacheReference<PersistentCache> reference;

        private ReferenceTrackingPersistentCache(DirCacheReference<PersistentCache> reference) {
            this.reference = reference;
            reference.addReference(this);
        }

        @Override
        public String toString() {
            return reference.cache.toString();
        }

        @Override
        public void close() {
            reference.release(this);
        }

        @Override
        public String getDisplayName() {
            return reference.cache.toString();
        }

        @Override
        public File getBaseDir() {
            return reference.cache.getBaseDir();
        }

        @Override
        public Collection<File> getReservedCacheFiles() {
            return reference.cache.getReservedCacheFiles();
        }

        @Override
        public <K, V> IndexedCache<K, V> createIndexedCache(IndexedCacheParameters<K, V> parameters) {
            return reference.cache.createIndexedCache(parameters);
        }

        @Override
        public <K, V> IndexedCache<K, V> createIndexedCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            return reference.cache.createIndexedCache(name, keyType, valueSerializer);
        }

        @Override
        public <K, V> boolean indexedCacheExists(IndexedCacheParameters<K, V> parameters) {
            return reference.cache.indexedCacheExists(parameters);
        }

        @Override
        public <T> T withFileLock(Supplier<? extends T> action) {
            return reference.cache.withFileLock(action);
        }

        @Override
        public void withFileLock(Runnable action) {
            reference.cache.withFileLock(action);
        }

        @Override
        public <T> T useCache(Supplier<? extends T> action) {
            return reference.cache.useCache(action);
        }

        @Override
        public void useCache(Runnable action) {
            reference.cache.useCache(action);
        }

        @Override
        public void cleanup() {
            reference.cache.cleanup();
        }
    }

    private static class ReferenceTrackingFineGrainedCache implements FineGrainedPersistentCache {
        private final DirCacheReference<FineGrainedPersistentCache> reference;

        public ReferenceTrackingFineGrainedCache(DirCacheReference<FineGrainedPersistentCache> reference) {
            this.reference = reference;
            reference.addReference(this);
        }

        @Override
        public FineGrainedPersistentCache open() {
            return reference.cache.open();
        }

        @Override
        public <T> T useCache(String key, Supplier<? extends T> action) {
            return reference.cache.useCache(key, action);
        }

        @Override
        public void useCache(String key, Runnable action) {
            reference.cache.useCache(key, action);
        }

        @Override
        public <T> T withFileLock(String key, Supplier<? extends T> action) {
            return reference.cache.withFileLock(key, action);
        }

        @Override
        public void withFileLock(String key, Runnable action) {
            reference.cache.withFileLock(key, action);
        }

        @Override
        public File getBaseDir() {
            return reference.cache.getBaseDir();
        }

        @Override
        public Collection<File> getReservedCacheFiles() {
            return reference.cache.getReservedCacheFiles();
        }

        @Override
        public String getDisplayName() {
            return reference.cache.getDisplayName();
        }

        @Override
        public void cleanup() {
            reference.cache.cleanup();
        }

        @Override
        public void close() {
            reference.release(this);
        }

        @Override
        public String toString() {
            return reference.cache.toString();
        }
    }
}
