/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.cached.ivy;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.internal.resource.cached.CachedItem;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

public abstract class AbstractCachedIndex<K, V extends CachedItem> {
    private final String persistentCacheFile;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final CacheLockingManager cacheLockingManager;

    private PersistentIndexedCache<K, V> persistentCache;

    public AbstractCachedIndex(String persistentCacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer, CacheLockingManager cacheLockingManager) {

        this.persistentCacheFile = persistentCacheFile;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<K, V> getPersistentCache() {
        if (persistentCache == null) {
            persistentCache = initPersistentCache();
        }
        return persistentCache;
    }

    private PersistentIndexedCache<K, V> initPersistentCache() {
        return cacheLockingManager.createCache(persistentCacheFile, keySerializer, valueSerializer);
    }

    private String operationName(String action) {
        return action + " artifact resolution cache '" + persistentCacheFile + "'";
    }

    public V lookup(final K key) {
        assertKeyNotNull(key);

        return cacheLockingManager.useCache(operationName("lookup from"), new Factory<V>() {
            public V create() {
                V found = getPersistentCache().get(key);
                if (found == null) {
                    return null;
                } else if (found.isMissing() || found.getCachedFile().exists()) {
                    return found;
                } else {
                    clear(key);
                    return null;
                }
            }
        });
    }

    protected void storeInternal(final K key, final V entry) {
        cacheLockingManager.useCache(operationName("store into"), new Runnable() {
            public void run() {
                getPersistentCache().put(key, entry);
            }
        });
    }

    protected void assertKeyNotNull(K key) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
    }

    protected void assertArtifactFileNotNull(File artifactFile) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
    }

    public void clear(final K key) {
        assertKeyNotNull(key);
        cacheLockingManager.useCache(operationName("clear from"), new Runnable() {
            public void run() {
                getPersistentCache().remove(key);
            }
        });
    }
}
