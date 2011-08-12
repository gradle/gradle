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

    public PersistentCache openStore(final File storeDir, final FileLockManager.LockMode lockMode, final CrossVersionMode crossVersionMode, final Action<? super PersistentCache> initializer) throws CacheOpenException {
        Factory<PersistentCache> factory = new Factory<PersistentCache>() {
            public PersistentCache create() {
                return cacheFactory.openStore(storeDir, lockMode, crossVersionMode, initializer);
            }
        };
        return new LazyCreationProxy<PersistentCache>(PersistentCache.class, factory).getSource();
    }

    public PersistentCache open(final File cacheDir, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final CrossVersionMode crossVersionMode, final Action<? super PersistentCache> initializer) throws CacheOpenException {
        Factory<PersistentCache> factory = new Factory<PersistentCache>() {
            public PersistentCache create() {
                return cacheFactory.open(cacheDir, usage, new HashMap<String, Object>(properties), lockMode, crossVersionMode, initializer);
            }
        };
        return new LazyCreationProxy<PersistentCache>(PersistentCache.class, factory).getSource();
    }

    public <E> PersistentStateCache<E> openStateCache(final File cacheDir, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final CrossVersionMode crossVersionMode, final Serializer<E> serializer) throws CacheOpenException {
        Factory<PersistentStateCache<E>> factory = new Factory<PersistentStateCache<E>>() {
            public PersistentStateCache<E> create() {
                return cacheFactory.openStateCache(cacheDir, usage, new HashMap<String, Object>(properties), lockMode, crossVersionMode, serializer);
            }
        };
        return new LazyCreationProxy<PersistentStateCache>(PersistentStateCache.class, factory).getSource();
    }

    public <K, V> PersistentIndexedCache<K, V> openIndexedCache(final File cacheDir, final CacheUsage usage, final Map<String, ?> properties, final FileLockManager.LockMode lockMode, final CrossVersionMode crossVersionMode, final Serializer<V> serializer) throws CacheOpenException {
        Factory<PersistentIndexedCache<K, V>> factory = new Factory<PersistentIndexedCache<K, V>>() {
            public PersistentIndexedCache<K, V> create() {
                return cacheFactory.openIndexedCache(cacheDir, usage, new HashMap<String, Object>(properties), lockMode, crossVersionMode, serializer);
            }
        };
        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
    }
}
