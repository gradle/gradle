/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.resource.cached;

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.cache.IndexedCache;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

public abstract class AbstractCachedIndex<K, V extends CachedItem> {
    private final String persistentCacheName;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator;
    private final FileAccessTracker fileAccessTracker;

    private IndexedCache<K, V> indexedCache;

    public AbstractCachedIndex(String persistentCacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, FileAccessTracker fileAccessTracker) {

        this.persistentCacheName = persistentCacheName;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.cacheAccessCoordinator = cacheAccessCoordinator;
        this.fileAccessTracker = fileAccessTracker;
    }

    private IndexedCache<K, V> getIndexedCache() {
        if (indexedCache == null) {
            indexedCache = initPersistentCache();
        }
        return indexedCache;
    }

    private IndexedCache<K, V> initPersistentCache() {
        return cacheAccessCoordinator.createCache(persistentCacheName, keySerializer, valueSerializer);
    }

    public V lookup(final K key) {
        assertKeyNotNull(key);

        V result = cacheAccessCoordinator.useCache(() -> {
            V found = getIndexedCache().getIfPresent(key);
            if (found == null) {
                return null;
            } else if (found.isMissing() || found.getCachedFile().exists()) {
                return found;
            } else {
                clear(key);
                return null;
            }
        });

        if (result != null && result.getCachedFile() != null) {
            fileAccessTracker.markAccessed(result.getCachedFile());
        }

        return result;
    }

    protected void storeInternal(final K key, final V entry) {
        cacheAccessCoordinator.useCache(() -> getIndexedCache().put(key, entry));
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
        cacheAccessCoordinator.useCache(() -> getIndexedCache().remove(key));
    }
}
