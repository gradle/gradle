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
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptions;
import org.gradle.internal.Factory;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.cache.internal.FileLockManager.LockMode;

public class DefaultCacheFactory implements Factory<CacheFactory> {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();
    private final FileLockManager lockManager;
    private final Lock lock = new ReentrantLock();

    public DefaultCacheFactory(FileLockManager fileLockManager) {
        this.lockManager = fileLockManager;
    }

    public CacheFactory create() {
        return new LockingCacheFactory(new CacheFactoryImpl());
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    public void close() {
        lock.lock();
        try {
            for (DirCacheReference dirCacheReference : dirCaches.values()) {
                dirCacheReference.close();
            }
        } finally {
            lock.unlock();
        }
    }

    private class LockingCacheFactory implements CacheFactory {
        private final CacheFactoryImpl delegate;

        private LockingCacheFactory(CacheFactoryImpl delegate) {
            this.delegate = delegate;
        }

        public PersistentCache open(File cacheDir, String displayName, CacheUsage usage, CacheValidator cacheValidator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
            lock.lock();
            try {
                return delegate.open(cacheDir, displayName, usage, cacheValidator, properties, lockOptions, initializer);
            } finally {
                lock.unlock();
            }
        }

        public PersistentCache openStore(File storeDir, String displayName, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
            lock.lock();
            try {
                return delegate.openStore(storeDir, displayName, lockOptions, initializer);
            } finally {
                lock.unlock();
            }
        }

        public void close() {
            lock.lock();
            try {
                delegate.close();
            } finally {
                lock.unlock();
            }
        }
    }

    private class CacheFactoryImpl implements CacheFactory {
        private final Set<BasicCacheReference<?>> caches = new LinkedHashSet<BasicCacheReference<?>>();

        private DirCacheReference doOpenDir(File cacheDir, String displayName, CacheUsage usage, CacheValidator validator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> action) {
            File canonicalDir = GFileUtils.canonicalise(cacheDir);
            DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
            if (dirCacheReference == null) {
                if (lockOptions.getMode().equals(LockMode.None)) {
                    // Create nested cache with LockMode#Exclusive (tb discussed) that is opened and closed on Demand in the DelegateOnDemandPersistentDirectoryCache.
                    DefaultPersistentDirectoryCache nestedCache = new DefaultPersistentDirectoryCache(canonicalDir, displayName, usage, validator, properties, lockOptions.withMode(LockMode.Exclusive), action, lockManager);
                    DelegateOnDemandPersistentDirectoryCache onDemandDache = new DelegateOnDemandPersistentDirectoryCache(nestedCache);
                    onDemandDache.open();
                    dirCacheReference = new DirCacheReference(onDemandDache, properties, lockOptions);
                    dirCaches.put(canonicalDir, dirCacheReference);
                } else {
                    ReferencablePersistentCache cache = new DefaultPersistentDirectoryCache(canonicalDir, displayName, usage, validator, properties, lockOptions, action, lockManager);
                    cache.open();
                    dirCacheReference = new DirCacheReference(cache, properties, lockOptions);
                    dirCaches.put(canonicalDir, dirCacheReference);
                }
            } else {
                if (usage == CacheUsage.REBUILD && dirCacheReference.rebuiltBy != this) {
                    throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
                }
                if (!lockOptions.equals(dirCacheReference.lockOptions)) {
                    throw new IllegalStateException(String.format("Cache '%s' is already open with different options.", cacheDir));
                }
                if (!properties.equals(dirCacheReference.properties)) {
                    throw new IllegalStateException(String.format("Cache '%s' is already open with different state.", cacheDir));
                }
            }
            if (usage == CacheUsage.REBUILD) {
                dirCacheReference.rebuiltBy = this;
            }
            dirCacheReference.addReference(this);
            return dirCacheReference;
        }

        public PersistentCache openStore(File storeDir, String displayName, LockOptions lockOptions, Action<? super PersistentCache> initializer) throws CacheOpenException {
            if (initializer != null) {
                throw new UnsupportedOperationException("Initializer actions are not currently supported by the directory store implementation.");
            }
            File canonicalDir = GFileUtils.canonicalise(storeDir);
            DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
            if (dirCacheReference == null) {
                ReferencablePersistentCache cache = new DefaultPersistentDirectoryStore(canonicalDir, displayName, lockOptions, lockManager);
                cache.open();
                dirCacheReference = new DirCacheReference(cache, Collections.<String, Object>emptyMap(), lockOptions);
                dirCaches.put(canonicalDir, dirCacheReference);
            }
            dirCacheReference.addReference(this);
            return dirCacheReference.getCache();
        }

        public PersistentCache open(File cacheDir, String displayName, CacheUsage usage, CacheValidator cacheValidator, Map<String, ?> properties, LockOptions lockOptions, Action<? super PersistentCache> initializer) {
            DirCacheReference dirCacheReference = doOpenDir(cacheDir, displayName, usage, cacheValidator, properties, lockOptions, initializer);
            return dirCacheReference.getCache();
        }

        public void close() {
            try {
                List<BasicCacheReference<?>> caches = new ArrayList<BasicCacheReference<?>>(this.caches);
                Collections.reverse(caches);
                for (BasicCacheReference cache : caches) {
                    cache.release(this);
                }
            } finally {
                caches.clear();
            }
        }
    }

    private abstract class BasicCacheReference<T> {
        private Set<CacheFactoryImpl> references = new HashSet<CacheFactoryImpl>();
        private final T cache;

        protected BasicCacheReference(T cache) {
            this.cache = cache;
            onOpen(cache);
        }

        public T getCache() {
            return cache;
        }

        public void release(CacheFactoryImpl owner) {
            boolean removed = references.remove(owner);
            assert removed;
            if (references.isEmpty()) {
                onClose(cache);
                close();
            }
        }

        public void addReference(CacheFactoryImpl owner) {
            references.add(owner);
            owner.caches.add(this);
        }

        public void close() {
        }
    }

    private class DirCacheReference extends BasicCacheReference<ReferencablePersistentCache> {
        private final Map<String, ?> properties;
        private final LockOptions lockOptions;
        CacheFactoryImpl rebuiltBy;

        public DirCacheReference(ReferencablePersistentCache cache, Map<String, ?> properties, LockOptions lockOptions) {
            super(cache);
            this.properties = properties;
            this.lockOptions = lockOptions;
        }

        public void close() {
            dirCaches.values().remove(this);
            getCache().close();
        }
    }
}
