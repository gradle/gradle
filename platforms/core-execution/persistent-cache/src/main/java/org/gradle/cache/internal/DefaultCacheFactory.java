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
import org.gradle.cache.FileLockManager;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultCacheFactory implements CacheFactory, Closeable {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<>();
    private final FileLockManager lockManager;
    private final ManagedExecutor executor;
    private final Lock lock = new ReentrantLock();

    public DefaultCacheFactory(FileLockManager fileLockManager, ManagedExecutor executor) {
        this.lockManager = fileLockManager;
        this.executor = executor;
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    @Override
    public PersistentCache open(File cacheDir, String displayName, Map<String, ?> properties, LockOptions lockOptions, @Nullable Consumer<? super PersistentCache> initializer, CacheCleanupStrategy cacheCleanupStrategy) throws CacheOpenException {
        lock.lock();
        try {
            return doOpen(cacheDir, displayName, properties, lockOptions, initializer, cacheCleanupStrategy);
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
            CompositeStoppable.stoppable(dirCaches.values(), executor).stop();
        } finally {
            dirCaches.clear();
            lock.unlock();
        }
    }

    private PersistentCache doOpen(
        File cacheDir,
        String displayName,
        Map<String, ?> properties,
        LockOptions lockOptions,
        @Nullable Consumer<? super PersistentCache> initializer,
        CacheCleanupStrategy cacheCleanupStrategy
    ) {
        DirCacheReference dirCacheReference = dirCaches.get(cacheDir);
        if (dirCacheReference == null) {
            ReferencablePersistentCache cache;
            if (!properties.isEmpty() || initializer != null) {
                Consumer<? super PersistentCache> initAction = initializer != null ? initializer : __ -> {};
                cache = new DefaultPersistentDirectoryCache(cacheDir, displayName, properties, lockOptions, initAction, cacheCleanupStrategy, lockManager, executor);
            } else {
                cache = new DefaultPersistentDirectoryStore(cacheDir, displayName, lockOptions, cacheCleanupStrategy, lockManager, executor);
            }
            cache.open();
            dirCacheReference = new DirCacheReference(cache, properties, lockOptions);
            dirCaches.put(cacheDir, dirCacheReference);
        } else {
            if (!lockOptions.equals(dirCacheReference.lockOptions)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different lock options.", cacheDir));
            }
            if (!properties.equals(dirCacheReference.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different properties.", cacheDir));
            }
        }
        return new ReferenceTrackingCache(dirCacheReference);
    }

    private class DirCacheReference implements Closeable {
        private final Map<String, ?> properties;
        private final LockOptions lockOptions;
        private final ReferencablePersistentCache cache;
        private final Set<ReferenceTrackingCache> references = new HashSet<>();

        DirCacheReference(ReferencablePersistentCache cache, Map<String, ?> properties, LockOptions lockOptions) {
            this.cache = cache;
            this.properties = properties;
            this.lockOptions = lockOptions;
            onOpen(cache);
        }

        public void addReference(ReferenceTrackingCache cache) {
            references.add(cache);
        }

        public void release(ReferenceTrackingCache cache) {
            lock.lock();
            try {
                if (references.remove(cache) && references.isEmpty()) {
                    close();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() {
            onClose(cache);
            dirCaches.values().remove(this);
            references.clear();
            cache.close();
        }
    }

    private static class ReferenceTrackingCache implements PersistentCache {
        private final DirCacheReference reference;

        private ReferenceTrackingCache(DirCacheReference reference) {
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
}
