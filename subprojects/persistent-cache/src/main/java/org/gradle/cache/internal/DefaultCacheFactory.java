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

import org.gradle.api.Action;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
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

public class DefaultCacheFactory implements CacheFactory, Closeable {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();
    private final FileLockManager lockManager;
    private final ExecutorFactory executorFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Lock lock = new ReentrantLock();

    public DefaultCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory, ProgressLoggerFactory progressLoggerFactory) {
        this.lockManager = fileLockManager;
        this.executorFactory = executorFactory;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    @Override
    public PersistentCache open(File cacheDir, String displayName, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initializer, CleanupAction cleanup) throws CacheOpenException {
        lock.lock();
        try {
            return doOpen(cacheDir, displayName, properties, lockTarget, lockOptions, initializer, cleanup);
        } finally {
            lock.unlock();
        }
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

    private PersistentCache doOpen(File cacheDir, String displayName, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, @Nullable Action<? super PersistentCache> initializer, @Nullable CleanupAction cleanup) {
        File canonicalDir = FileUtils.canonicalize(cacheDir);
        DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
        if (dirCacheReference == null) {
            ReferencablePersistentCache cache;
            if (!properties.isEmpty() || initializer != null) {
                cache = new DefaultPersistentDirectoryCache(canonicalDir, displayName, properties, lockTarget, lockOptions, initializer, cleanup, lockManager, executorFactory, progressLoggerFactory);
            } else {
                cache = new DefaultPersistentDirectoryStore(canonicalDir, displayName, lockTarget, lockOptions, cleanup, lockManager, executorFactory, progressLoggerFactory);
            }
            cache.open();
            dirCacheReference = new DirCacheReference(cache, properties, lockTarget, lockOptions);
            dirCaches.put(canonicalDir, dirCacheReference);
        } else {
            if (!lockOptions.equals(dirCacheReference.lockOptions)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different lock options.", cacheDir));
            }
            if (lockTarget != dirCacheReference.lockTarget) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different lock target.", cacheDir));
            }
            if (!properties.equals(dirCacheReference.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different properties.", cacheDir));
            }
        }
        return new ReferenceTrackingCache(dirCacheReference);
    }

    private class DirCacheReference implements Closeable {
        private final Map<String, ?> properties;
        private final CacheBuilder.LockTarget lockTarget;
        private final LockOptions lockOptions;
        private final ReferencablePersistentCache cache;
        private final Set<ReferenceTrackingCache> references = new HashSet<ReferenceTrackingCache>();

        DirCacheReference(ReferencablePersistentCache cache, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions) {
            this.cache = cache;
            this.properties = properties;
            this.lockTarget = lockTarget;
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
        public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
            return reference.cache.createCache(parameters);
        }

        @Override
        public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            return reference.cache.createCache(name, keyType, valueSerializer);
        }

        @Override
        public <T> T withFileLock(Factory<? extends T> action) {
            return reference.cache.withFileLock(action);
        }

        @Override
        public void withFileLock(Runnable action) {
            reference.cache.withFileLock(action);
        }

        @Override
        public <T> T useCache(Factory<? extends T> action) {
            return reference.cache.useCache(action);
        }

        @Override
        public void useCache(Runnable action) {
            reference.cache.useCache(action);
        }
    }
}
