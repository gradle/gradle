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

package org.gradle.api.internal.externalresource.ivy;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.externalresource.cached.CachedItem;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;

import java.io.File;
import java.io.Serializable;

abstract public class AbstractCachedIndex<K extends Serializable, V extends CachedItem> {

    private final File persistentCacheFile;
    private final Class<K> keyTypeClass;
    private final Class<V> valueTypeClass;
    private final CacheLockingManager cacheLockingManager;

    private PersistentIndexedCache<K, V> persistentCache;

    public AbstractCachedIndex(File persistentCacheFile, Class<K> keyTypeClass, Class<V> valueTypeClass, CacheLockingManager cacheLockingManager) {

        this.persistentCacheFile = persistentCacheFile;
        this.keyTypeClass = keyTypeClass;
        this.valueTypeClass = valueTypeClass;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<K, V> getPersistentCache() {
        if (persistentCache == null) {
            persistentCache = initPersistentCache();
        }
        return persistentCache;
    }


    private PersistentIndexedCache<K, V> initPersistentCache() {
        return cacheLockingManager.createCache(persistentCacheFile, keyTypeClass, valueTypeClass);
    }


    private String operationName(String action) {
        return String.format("%s artifact resolution cache '%s'", action, persistentCacheFile.getName());
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
