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
import org.gradle.api.internal.Factory;
import org.gradle.cache.*;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;

import static org.gradle.cache.internal.FileLockManager.LockMode;

public class DefaultCacheFactory implements Factory<CacheFactory> {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();
    private final FileLockManager lockManager = new DefaultFileLockManager();

    public CacheFactory create() {
        return new CacheFactoryImpl();
    }

    void onOpen(Object cache) {
    }

    void onClose(Object cache) {
    }

    public void close() {
        for (DirCacheReference dirCacheReference : dirCaches.values()) {
            dirCacheReference.close();
        }
    }

    private class CacheFactoryImpl implements CacheFactory {
        private final Set<BasicCacheReference<?>> caches = new LinkedHashSet<BasicCacheReference<?>>();

        private DirCacheReference doOpenDir(File cacheDir, CacheUsage usage, Map<String, ?> properties, FileLockManager.LockMode lockMode, Action<? super PersistentCache> action) {
            File canonicalDir = GFileUtils.canonicalise(cacheDir);
            DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
            if (dirCacheReference == null) {
                DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(canonicalDir, usage, properties, lockMode, action, lockManager);
                dirCacheReference = new DirCacheReference(cache, properties, lockMode);
                dirCaches.put(canonicalDir, dirCacheReference);
            } else {
                if (usage == CacheUsage.REBUILD && dirCacheReference.rebuiltBy != this) {
                    throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
                }
                if (lockMode != dirCacheReference.lockMode) {
                    throw new IllegalStateException(String.format("Cannot open cache '%s' with %s lock mode as it is already open with %s lock mode.", cacheDir, lockMode.toString().toLowerCase(), dirCacheReference.lockMode.toString().toLowerCase()));
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

        public PersistentCache openStore(File storeDir, LockMode lockMode, CrossVersionMode crossVersionMode, Action<? super PersistentCache> initializer) throws CacheOpenException {
            if (lockMode == LockMode.Exclusive) {
                throw new UnsupportedOperationException("No exclusive mode directory store implementation currently available.");
            }
            if (initializer != null) {
                throw new UnsupportedOperationException("Initializer actions are not currently supported by the directory store implementation.");
            }
            return new DefaultPersistentDirectoryStore(storeDir);
        }

        public PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode, Action<? super PersistentCache> initializer) {
            DirCacheReference dirCacheReference = doOpenDir(cacheDir, usage, properties, lockMode, initializer);
            return dirCacheReference.getCache();
        }

        public <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode, Serializer<E> serializer) {
            StateCacheReference<E> cacheReference = doOpenDir(cacheDir, usage, properties, lockMode, null).getStateCache(serializer);
            cacheReference.addReference(this);
            return cacheReference.getCache();
        }

        public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode, Serializer<V> serializer) {
            if (lockMode == LockMode.Shared) {
                throw new UnsupportedOperationException("No indexed cache implementation is available that can be shared by multiple processes.");
            }
            if (crossVersionMode == CrossVersionMode.CrossVersion) {
                throw new UnsupportedOperationException("No indexed cache implementation is available that can be used by multiple Gradle versions.");
            }
            IndexedCacheReference<K, V> cacheReference = doOpenDir(cacheDir, usage, properties, LockMode.Exclusive, null).getIndexedCache(serializer);
            cacheReference.addReference(this);
            return cacheReference.getCache();
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

    private class DirCacheReference extends BasicCacheReference<DefaultPersistentDirectoryCache> {
        private final Map<String, ?> properties;
        private final FileLockManager.LockMode lockMode;
        IndexedCacheReference indexedCache;
        StateCacheReference stateCache;
        CacheFactoryImpl rebuiltBy;

        public DirCacheReference(DefaultPersistentDirectoryCache cache, Map<String, ?> properties, FileLockManager.LockMode lockMode) {
            super(cache);
            this.properties = properties;
            this.lockMode = lockMode;
        }

        public <E> StateCacheReference<E> getStateCache(Serializer<E> serializer) {
            if (stateCache == null) {
                SimpleStateCache<E> stateCache = new SimpleStateCache<E>(new File(getCache().getBaseDir(), "state.bin"), getCache().getLock(), serializer);
                this.stateCache = new StateCacheReference<E>(stateCache, this);
            }
            return stateCache;
        }

        public <K, V> IndexedCacheReference<K, V> getIndexedCache(Serializer<V> serializer) {
            if (indexedCache == null) {
                BTreePersistentIndexedCache<K, V> indexedCache = new BTreePersistentIndexedCache<K, V>(new File(getCache().getBaseDir(), "cache.bin"), getCache().getLock(), serializer);
                this.indexedCache = new IndexedCacheReference<K, V>(indexedCache, this);
            }
            return indexedCache;
        }

        public void close() {
            dirCaches.values().remove(this);
            getCache().close();
        }
    }

    private abstract class NestedCacheReference<T> extends BasicCacheReference<T> {
        protected final DefaultCacheFactory.DirCacheReference backingCache;

        protected NestedCacheReference(T cache, DirCacheReference backingCache) {
            super(cache);
            this.backingCache = backingCache;
        }
    }

    private class IndexedCacheReference<K, V> extends NestedCacheReference<BTreePersistentIndexedCache<K, V>> {
        private IndexedCacheReference(BTreePersistentIndexedCache<K, V> cache, DirCacheReference backingCache) {
            super(cache, backingCache);
        }

        @Override
        public void close() {
            backingCache.indexedCache = null;
            getCache().close();
            super.close();
        }
    }

    private class StateCacheReference<E> extends NestedCacheReference<SimpleStateCache<E>> {
        private StateCacheReference(SimpleStateCache<E> cache, DirCacheReference backingCache) {
            super(cache, backingCache);
        }

        @Override
        public void close() {
            backingCache.stateCache = null;
            super.close();
        }
    }
}
