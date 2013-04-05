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
package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.Factory;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.listener.LazyCreationProxy;

import java.io.File;

public class DefaultTaskArtifactStateCacheAccess implements TaskArtifactStateCacheAccess {
    private final Gradle gradle;
    private final CacheRepository cacheRepository;
    private PersistentCache cache;

    public DefaultTaskArtifactStateCacheAccess(Gradle gradle, CacheRepository cacheRepository) {
        this.gradle = gradle;
        this.cacheRepository = cacheRepository;
    }

    private PersistentCache getCache() {
        if (cache == null) {
            cache = cacheRepository
                    .cache("taskArtifacts")
                    .forObject(gradle)
                    .withDisplayName("task artifact state cache")
                    .withLockMode(FileLockManager.LockMode.Exclusive)
                    .open();
        }
        return cache;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(final String cacheName, final Class<K> keyType, final Class<V> valueType) {
        Factory<PersistentIndexedCache> factory = new Factory<PersistentIndexedCache>() {
            public PersistentIndexedCache create() {
                return getCache().createCache(cacheFile(cacheName), keyType, valueType);
            }
        };
        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(final String cacheName, final Class<K> keyType, final Class<V> valueType, final Serializer<V> valueSerializer) {
        Factory<PersistentIndexedCache> factory = new Factory<PersistentIndexedCache>() {
            public PersistentIndexedCache create() {
                return getCache().createCache(cacheFile(cacheName), keyType, valueSerializer);
            }
        };
        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();

    }

    private File cacheFile(String cacheName) {
        return new File(getCache().getBaseDir(), cacheName + ".bin");
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return getCache().useCache(operationDisplayName, action);
    }

    public void useCache(String operationDisplayName, Runnable action) {
        getCache().useCache(operationDisplayName, action);
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        getCache().longRunningOperation(operationDisplayName, action);
    }
}