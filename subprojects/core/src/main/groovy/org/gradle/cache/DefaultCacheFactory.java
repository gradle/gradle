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
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.api.internal.Factory;
import org.gradle.cache.btree.BTreePersistentIndexedCache;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.*;

public class DefaultCacheFactory implements Factory<CacheFactory> {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();

    public CacheFactory create() {
        return new CacheFactoryImpl();
    }

    private DefaultPersistentDirectoryCache createCache(CacheUsage usage, Map<String, ?> properties, File canonicalDir) {
        return new DefaultPersistentDirectoryCache(canonicalDir, usage, properties);
    }

    private class CacheFactoryImpl implements CacheFactory {
        private final Collection<BasicCacheReference> caches = new HashSet<BasicCacheReference>();

        private DirCacheReference doOpenDir(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
            File canonicalDir = GFileUtils.canonicalise(cacheDir);
            DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
            if (dirCacheReference == null) {
                DefaultPersistentDirectoryCache cache = createCache(usage, properties, canonicalDir);
                dirCacheReference = new DirCacheReference(cache, properties);
                dirCaches.put(canonicalDir, dirCacheReference);
            } else {
                if (usage == CacheUsage.REBUILD && dirCacheReference.rebuiltBy != this) {
                    throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
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

        public PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
            DirCacheReference dirCacheReference = doOpenDir(cacheDir, usage, properties);
            caches.add(dirCacheReference);
            return dirCacheReference.getCache();
        }

        public <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<E> serializer) {
            StateCacheReference<E> cacheReference = doOpenDir(cacheDir, usage, properties).getStateCache(serializer);
            caches.add(cacheReference);
            cacheReference.addReference(this);
            return cacheReference.getCache();
        }

        public <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<V> serializer) {
            IndexedCacheReference<K, V> cacheReference = doOpenDir(cacheDir, usage, properties).getIndexedCache(serializer);
            caches.add(cacheReference);
            cacheReference.addReference(this);
            return cacheReference.getCache();
        }

        public void close() {
            try {
                for (BasicCacheReference cache : caches) {
                    cache.release(this);
                }
            } finally {
                caches.clear();
            }
        }
    }

    private abstract class BasicCacheReference {
        private Set<CacheFactoryImpl> references = new HashSet<CacheFactoryImpl>();

        public void release(CacheFactoryImpl owner) {
            boolean removed = references.remove(owner);
            assert removed;
            if (references.isEmpty()) {
                close();
            }
        }

        public void addReference(CacheFactoryImpl owner) {
            references.add(owner);
        }

        public void close() {
        }
    }

    private class DirCacheReference extends BasicCacheReference {
        private final DefaultPersistentDirectoryCache cache;
        private final Map<String, ?> properties;
        IndexedCacheReference indexedCache;
        StateCacheReference stateCache;
        CacheFactoryImpl rebuiltBy;

        public DirCacheReference(DefaultPersistentDirectoryCache cache, Map<String, ?> properties) {
            this.cache = cache;
            this.properties = properties;
        }

        public PersistentCache getCache() {
            return cache;
        }

        public <E> StateCacheReference<E> getStateCache(Serializer<E> serializer) {
            if (stateCache == null) {
                SimpleStateCache<E> stateCache = new SimpleStateCache<E>(cache, serializer);
                this.stateCache = new StateCacheReference<E>(stateCache, this);
            }
            return stateCache;
        }

        public <K, V> IndexedCacheReference<K, V> getIndexedCache(Serializer<V> serializer) {
            if (indexedCache == null) {
                BTreePersistentIndexedCache<K, V> indexedCache = new BTreePersistentIndexedCache<K, V>(cache, serializer);
                this.indexedCache = new IndexedCacheReference<K, V>(indexedCache, this);
            }
            return indexedCache;
        }

        public void close() {
            dirCaches.values().remove(this);
        }
    }

    private abstract class NestedCacheReference extends BasicCacheReference {
        protected final DefaultCacheFactory.DirCacheReference backingCache;

        protected NestedCacheReference(DirCacheReference backingCache) {
            this.backingCache = backingCache;
        }
    }

    private class IndexedCacheReference<K, V> extends NestedCacheReference {
        private final BTreePersistentIndexedCache<K, V> cache;

        private IndexedCacheReference(BTreePersistentIndexedCache<K, V> cache, DirCacheReference backingCache) {
            super(backingCache);
            this.cache = cache;
        }

        public PersistentIndexedCache<K, V> getCache() {
            return cache;
        }

        @Override
        public void close() {
            super.close();
            backingCache.indexedCache = null;
            cache.close();
        }
    }

    private class StateCacheReference<E> extends NestedCacheReference {
        private final SimpleStateCache<E> cache;

        private StateCacheReference(SimpleStateCache<E> cache, DirCacheReference backingCache) {
            super(backingCache);
            this.cache = cache;
        }

        public PersistentStateCache<E> getCache() {
            return cache;
        }

        @Override
        public void close() {
            super.close();
            backingCache.stateCache = null;
        }
    }
}
