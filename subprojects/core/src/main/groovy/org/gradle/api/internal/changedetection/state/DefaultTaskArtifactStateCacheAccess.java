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

import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.PersistentIndexedCacheParameters;
import org.gradle.internal.Factory;
import org.gradle.listener.LazyCreationProxy;
import org.gradle.messaging.serialize.Serializer;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTaskArtifactStateCacheAccess implements TaskArtifactStateCacheAccess {
    private final Gradle gradle;
    private final CacheRepository cacheRepository;
    private InMemoryPersistentCacheDecorator inMemoryDecorator;
    private PersistentCache cache;
    private final Object lock = new Object();

    public DefaultTaskArtifactStateCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryPersistentCacheDecorator decorator) {
        this.gradle = gradle;
        this.cacheRepository = cacheRepository;
        this.inMemoryDecorator = decorator;
    }

    private PersistentCache getCache() {
        //TODO SF just do it in the constructor
        synchronized (lock) {
            if (cache == null) {
                cache = cacheRepository
                        .cache(gradle, "taskArtifacts")
                        .withDisplayName("task history cache")
                        .withLockOptions(mode(FileLockManager.LockMode.None)) // Lock on demand
                        .open();
            }
            return cache;
        }
    }

    public void close() {
        if (cache != null) {
            cache.close();
        }
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(final String cacheName, final Class<K> keyType, final Serializer<V> valueSerializer) {
        Factory<PersistentIndexedCache> factory = new Factory<PersistentIndexedCache>() {
            public PersistentIndexedCache create() {
                PersistentIndexedCacheParameters<K, V> parameters = new PersistentIndexedCacheParameters<K, V>(cacheName, keyType, valueSerializer)
                        .cacheDecorator(inMemoryDecorator);
                return getCache().createCache(parameters);
            }
        };
        return new LazyCreationProxy<PersistentIndexedCache>(PersistentIndexedCache.class, factory).getSource();
    }

    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return getCache().useCache(operationDisplayName, action);
    }

    public void useCache(String operationDisplayName, Runnable action) {
        getCache().useCache(operationDisplayName, action);
    }

    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return getCache().longRunningOperation(operationDisplayName, action);
    }

    public void longRunningOperation(String operationDisplayName, Runnable action) {
        getCache().longRunningOperation(operationDisplayName, action);
    }
}