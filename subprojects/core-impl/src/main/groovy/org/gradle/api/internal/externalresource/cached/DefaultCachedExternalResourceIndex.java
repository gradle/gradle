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

package org.gradle.api.internal.externalresource.cached;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;

import java.io.File;
import java.io.Serializable;

public class DefaultCachedExternalResourceIndex<K extends Serializable> implements CachedExternalResourceIndex<K> {

    private final File persistentCacheFile;
    private final TimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;

    private PersistentIndexedCache<K, DefaultCachedExternalResource> persistentCache;
    private final Class<K> keyType;

    public DefaultCachedExternalResourceIndex(File persistentCacheFile, Class<K> keyType, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.persistentCacheFile = persistentCacheFile;
        this.keyType = keyType;
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<K, DefaultCachedExternalResource> getPersistentCache() {
        if (persistentCache == null) {
            persistentCache = initPersistentCache();
        }
        return persistentCache;
    }

    private PersistentIndexedCache<K, DefaultCachedExternalResource> initPersistentCache() {
        return cacheLockingManager.createCache(persistentCacheFile, keyType, DefaultCachedExternalResource.class);
    }

    private DefaultCachedExternalResource createMissingEntry() {
        return new DefaultCachedExternalResource(timeProvider.getCurrentTime());
    }

    private DefaultCachedExternalResource createEntry(File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        return new DefaultCachedExternalResource(artifactFile, timeProvider.getCurrentTime(), externalResourceMetaData);
    }

    private String operationName(String action) {
        return String.format("%s artifact resolution cache '%s'", action, persistentCacheFile.getName());
    }

    public void store(final K key, final File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }

        storeInternal(key, createEntry(artifactFile, externalResourceMetaData));
    }

    public void storeMissing(K key) {
        storeInternal(key, createMissingEntry());
    }

    private void storeInternal(final K key, final DefaultCachedExternalResource entry) {
        cacheLockingManager.useCache(operationName("store into"), new Runnable() {
            public void run() {
                getPersistentCache().put(key, entry);
            }
        });
    }

    public CachedExternalResource lookup(final K key) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }

        return cacheLockingManager.useCache(operationName("lookup from"), new Factory<DefaultCachedExternalResource>() {
            public DefaultCachedExternalResource create() {
                DefaultCachedExternalResource found = getPersistentCache().get(key);
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

    public void clear(final K key) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }

        cacheLockingManager.useCache(operationName("clear from"), new Runnable() {
            public void run() {
                getPersistentCache().remove(key);
            }
        });
    }

}
