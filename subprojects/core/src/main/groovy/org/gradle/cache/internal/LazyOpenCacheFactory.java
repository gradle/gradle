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

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.api.internal.Factory;
import org.gradle.cache.*;
import org.gradle.listener.LazyCreationProxy;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LazyOpenCacheFactory implements CacheFactory {
    private final CacheFactory cacheFactory;

    public LazyOpenCacheFactory(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    public PersistentCache openStore(final File storeDir, final String displayName, final FileLockManager.LockMode lockMode, final Action<? super PersistentCache> initializer) throws CacheOpenException {
        Factory<PersistentCache> factory = new Factory<PersistentCache>() {
            public PersistentCache create() {
                return cacheFactory.openStore(storeDir, displayName, lockMode, initializer);
            }
        };
        return new LazyPersistentCache(factory);
    }

    public PersistentCache open(final File cacheDir, final String displayName, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final Action<? super PersistentCache> initializer) throws CacheOpenException {
        Factory<PersistentCache> factory = new Factory<PersistentCache>() {
            public PersistentCache create() {
                return cacheFactory.open(cacheDir, displayName, usage, new HashMap<String, Object>(properties), lockMode, initializer);
            }
        };
        return new LazyPersistentCache(factory);
    }

    public <E> PersistentStateCache<E> openStateCache(final File cacheDir, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final Serializer<E> serializer) throws CacheOpenException {
        Factory<PersistentStateCache<E>> factory = new Factory<PersistentStateCache<E>>() {
            public PersistentStateCache<E> create() {
                return cacheFactory.openStateCache(cacheDir, usage, new HashMap<String, Object>(properties), lockMode, serializer);
            }
        };
        return new LazyCreationProxy<PersistentStateCache>(PersistentStateCache.class, factory).getSource();
    }

    public <K, V> PersistentIndexedCache<K, V> openIndexedCache(final File cacheDir, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final Serializer<V> serializer) throws CacheOpenException {
        Factory<PersistentIndexedCache<K, V>> factory = new Factory<PersistentIndexedCache<K, V>>() {
            public PersistentIndexedCache<K, V> create() {
                return cacheFactory.openIndexedCache(cacheDir, usage, new HashMap<String, Object>(properties), lockMode, serializer);
            }
        };
        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
    }

    private static class LazyPersistentCache implements PersistentCache {
        private PersistentCache cache;
        private final Factory<PersistentCache> factory;

        private LazyPersistentCache(Factory<PersistentCache> factory) {
            this.factory = factory;
        }

        private PersistentCache getCache() {
            if (cache == null) {
                cache = factory.create();
            }
            return cache;
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(final File cacheFile, final Class<K> keyType, final Serializer<V> valueSerializer) {
            Factory<PersistentIndexedCache<K, V>> factory = new Factory<PersistentIndexedCache<K, V>>() {
                public PersistentIndexedCache<K, V> create() {
                    return getCache().createCache(cacheFile, keyType, valueSerializer);
                }
            };
            return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
        }

        public <K, V> PersistentIndexedCache<K, V> createCache(final File cacheFile, final Class<K> keyType, final Class<V> valueType) {
            Factory<PersistentIndexedCache<K, V>> factory = new Factory<PersistentIndexedCache<K, V>>() {
                public PersistentIndexedCache<K, V> create() {
                    return getCache().createCache(cacheFile, keyType, valueType);
                }
            };
            return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
        }

        public File getBaseDir() {
            return getCache().getBaseDir();
        }

        public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
            return getCache().longRunningOperation(operationDisplayName, action);
        }

        public void longRunningOperation(String operationDisplayName, Runnable action) {
            getCache().longRunningOperation(operationDisplayName, action);
        }

        public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
            return getCache().useCache(operationDisplayName, action);
        }
    }
}
