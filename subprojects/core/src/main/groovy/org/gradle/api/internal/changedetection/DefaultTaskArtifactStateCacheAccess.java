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
package org.gradle.api.internal.changedetection;

import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.Serializer;
import org.gradle.cache.internal.FileLockManager;

import java.io.File;

public class DefaultTaskArtifactStateCacheAccess implements TaskArtifactStateCacheAccess {
    private final PersistentCache cache;

    public DefaultTaskArtifactStateCacheAccess(Gradle gradle, CacheRepository cacheRepository) {
       cache = cacheRepository
                .cache("taskArtifacts")
                .forObject(gradle)
                .withDisplayName("task artifact state cache")
                .withLockMode(FileLockManager.LockMode.Exclusive)
                .open();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType) {
        return cache.createCache(cacheFile(cacheName), keyType, valueType);
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType, Serializer<V> valueSerializer) {
        return cache.createCache(cacheFile(cacheName), keyType, valueSerializer);
    }

    private File cacheFile(String cacheName) {
        return new File(cache.getBaseDir(), cacheName + ".bin");
    }
}