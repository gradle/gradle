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
import org.gradle.cache.btree.BTreePersistentIndexedCache;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultCacheFactory implements CacheFactory {
    private final Map<File, DirCacheReference> dirCaches = new HashMap<File, DirCacheReference>();

    public DirCacheReference open(File cacheDir, CacheUsage usage, Map<String, ?> properties) {
        File canonicalDir = GFileUtils.canonicalise(cacheDir);
        DirCacheReference dirCacheReference = dirCaches.get(canonicalDir);
        if (dirCacheReference == null) {
            DefaultPersistentDirectoryCache cache = createCache(usage, properties, canonicalDir);
            dirCacheReference = new DirCacheReference(cache, properties);
            dirCaches.put(canonicalDir, dirCacheReference);
        } else {
            if (usage == CacheUsage.REBUILD) {
                throw new IllegalStateException(String.format("Cannot rebuild cache '%s' as it is already open.", cacheDir));
            }
            if (!properties.equals(dirCacheReference.properties)) {
                throw new IllegalStateException(String.format("Cache '%s' is already open with different state.", cacheDir));
            }
        }
        dirCacheReference.addReference();
        return dirCacheReference;
    }

    public <K, V> CacheReference<PersistentIndexedCache<K, V>> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<V> serializer) {
        DirCacheReference cacheReference = open(cacheDir, usage, properties);
        if (cacheReference.indexedCache == null) {
            PersistentCache cache = cacheReference.getCache();
            BTreePersistentIndexedCache<K, V> indexedCache = new BTreePersistentIndexedCache<K, V>(cache, serializer);
            cacheReference.indexedCache = new IndexedCacheReference<K, V>(indexedCache, cacheReference);
        }
        cacheReference.indexedCache.addReference();
        return cacheReference.indexedCache;
    }

    public <E> CacheReference<PersistentStateCache<E>> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, Serializer<E> serializer) {
        DirCacheReference cacheReference = open(cacheDir, usage, properties);
        if (cacheReference.stateCache == null) {
            PersistentCache cache = cacheReference.getCache();
            SimpleStateCache<E> stateCache = new SimpleStateCache<E>(cache, serializer);
            cacheReference.stateCache = new StateCacheReference<E>(stateCache, cacheReference);
        }
        cacheReference.stateCache.addReference();
        return cacheReference.stateCache;
    }

    public void close() {
        try {
            for (DirCacheReference dirCacheReference : dirCaches.values()) {
                dirCacheReference.close();
            }
        } finally {
            dirCaches.clear();
        }
    }

    private DefaultPersistentDirectoryCache createCache(CacheUsage usage, Map<String, ?> properties, File canonicalDir) {
        return new DefaultPersistentDirectoryCache(canonicalDir, usage, properties);
    }

    private abstract class BasicCacheReference<T> implements CacheFactory.CacheReference<T> {
        private int references;

        public void release() {
            references--;
            if (references == 0) {
                close();
            }
        }

        public void addReference() {
            references++;
        }

        public void close() {
        }
    }

    private class DirCacheReference extends BasicCacheReference<PersistentCache> {
        private final DefaultPersistentDirectoryCache cache;
        private final Map<String, ?> properties;
        IndexedCacheReference indexedCache;
        StateCacheReference stateCache;

        public DirCacheReference(DefaultPersistentDirectoryCache cache, Map<String, ?> properties) {
            this.cache = cache;
            this.properties = properties;
        }

        public PersistentCache getCache() {
            return cache;
        }

        public void close() {
            dirCaches.values().remove(this);
        }
    }

    private abstract class NestedCacheReference<T> extends BasicCacheReference<T> {
        protected final DefaultCacheFactory.DirCacheReference backingCache;

        protected NestedCacheReference(DirCacheReference backingCache) {
            this.backingCache = backingCache;
        }

        @Override
        public void close() {
            backingCache.release();
        }
    }

    private class IndexedCacheReference<K, V> extends NestedCacheReference<PersistentIndexedCache<K, V>> {
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

    private class StateCacheReference<E> extends NestedCacheReference<PersistentStateCache<E>> {
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
