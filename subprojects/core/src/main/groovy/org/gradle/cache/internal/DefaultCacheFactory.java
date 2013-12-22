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

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.cache.*;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.util.GFileUtils;

import java.io.Closeable;
import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultCacheFactory implements CacheFactory {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();
    private final FileLockManager lockManager;
    private final Lock lock = new ReentrantLock();

    public DefaultCacheFactory(FileLockManager fileLockManager) {
        this.lockManager = fileLockManager;
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    public PersistentCache open(File cacheDir, String displayName, CacheUsage usage, CacheValidator cacheValidator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        lock.lock();
        try {
            return doOpen(cacheDir, displayName, usage, cacheValidator, properties, lockOptions, initializer);
        } finally {
            lock.unlock();
        }
    }

    public PersistentCache openStore(File storeDir, String displayName, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        lock.lock();
        try {
            return doOpenStore(storeDir, displayName, lockOptions, initializer);
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

    private PersistentCache doOpen(File cacheDir, String displayName, CacheUsage usage, CacheValidator validator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> action) {
        File canonicalDir = GFileUtils.canonicalise(cacheDir);
        DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
        if (dirCacheReference == null) {
            ReferencablePersistentCache cache = new DefaultPersistentDirectoryCache(canonicalDir, displayName, usage, validator, properties, lockOptions, action, lockManager);
            cache.open();
            dirCacheReference = new DirCacheReference(cache, properties, lockOptions, usage == CacheUsage.REBUILD);
            dirCaches.put(canonicalDir, dirCacheReference);
        } else {
            if (usage == CacheUsage.REBUILD && !dirCacheReference.rebuild) {
                throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
            }
            if (!lockOptions.equals(dirCacheReference.lockOptions)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different options.", cacheDir));
            }
            if (!properties.equals(dirCacheReference.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different state.", cacheDir));
            }
        }
        return new ReferenceTrackingCache(dirCacheReference);
    }

    private PersistentCache doOpenStore(File storeDir, String displayName, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
        if (initializer != null) {
            throw new UnsupportedOperationException("Initializer actions are not currently supported by the directory store implementation.");
        }
        File canonicalDir = GFileUtils.canonicalise(storeDir);
        DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
        if (dirCacheReference == null) {
            ReferencablePersistentCache cache = new DefaultPersistentDirectoryStore(canonicalDir, displayName, lockOptions, lockManager);
            cache.open();
            dirCacheReference = new DirCacheReference(cache, Collections.<String, Object>emptyMap(), lockOptions, false);
            dirCaches.put(canonicalDir, dirCacheReference);
        }
        return new ReferenceTrackingCache(dirCacheReference);
    }

    private class DirCacheReference implements Closeable {
        private final Map<String, ?> properties;
        private final LockOptions lockOptions;
        private final ReferencablePersistentCache cache;
        private final Set<ReferenceTrackingCache> references = new HashSet<ReferenceTrackingCache>();
        private final boolean rebuild;

        public DirCacheReference(ReferencablePersistentCache cache, Map<String, ?> properties, LockOptions lockOptions, boolean rebuild) {
            this.cache = cache;
            this.properties = properties;
            this.lockOptions = lockOptions;
            this.rebuild = rebuild;
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
