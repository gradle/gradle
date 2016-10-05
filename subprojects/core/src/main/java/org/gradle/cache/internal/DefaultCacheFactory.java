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
import org.gradle.api.Nullable;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.serialize.Serializer;

import java.io.Closeable;
import java.io.File;
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
    public PersistentCache open(File cacheDir, String displayName, @Nullable CacheValidator cacheValidator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        lock.lock();
        try {
            return doOpen(cacheDir, displayName, cacheValidator, properties, lockTarget, lockOptions, initializer);
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            CompositeStoppable.stoppable(dirCaches.values()).stop();
        } finally {
            dirCaches.clear();
            lock.unlock();
        }
    }

    private PersistentCache doOpen(File cacheDir, String displayName, @Nullable CacheValidator validator, Map<String, ?> properties, CacheBuilder.LockTarget lockTarget, LockOptions lockOptions, @Nullable Action<? super PersistentCache> initializer) {
        File canonicalDir = FileUtils.canonicalize(cacheDir);
        DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
        if (dirCacheReference == null) {
            ReferencablePersistentCache cache;
            if (!properties.isEmpty() || validator != null || initializer != null) {
                cache = new DefaultPersistentDirectoryCache(canonicalDir, displayName, validator, properties, lockTarget, lockOptions, initializer, lockManager, executorFactory);
            } else {
                cache = new DefaultPersistentDirectoryStore(canonicalDir, displayName, lockTarget, lockOptions, lockManager, executorFactory);
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

        public void close() {
            reference.release(this);
        }

        public File getBaseDir() {
            return reference.cache.getBaseDir();
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(PersistentIndexedCacheParameters<K, V> parameters) {
            return reference.cache.createCache(parameters);
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
            return reference.cache.createCache(name, keyType, valueSerializer);
        }

        @Override
        public void flush() {
            reference.cache.flush();
        }

        public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
            return reference.cache.longRunningOperation(operationDisplayName, action);
        }

        public void longRunningOperation(String operationDisplayName, Runnable action) {
            reference.cache.longRunningOperation(operationDisplayName, action);
        }

        public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
            return reference.cache.useCache(operationDisplayName, action);
        }

        public void useCache(String operationDisplayName, Runnable action) {
            reference.cache.useCache(operationDisplayName, action);
        }
    }
}
