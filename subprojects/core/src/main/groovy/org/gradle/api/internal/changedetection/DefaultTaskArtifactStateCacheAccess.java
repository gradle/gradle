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
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.Serializer;

public class DefaultTaskArtifactStateCacheAccess implements TaskArtifactStateCacheAccess {
    private final Gradle gradle;
    private final CacheRepository cacheRepository;

    public DefaultTaskArtifactStateCacheAccess(Gradle gradle, CacheRepository cacheRepository) {
        this.gradle = gradle;
        this.cacheRepository = cacheRepository;
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType) {
        return cacheRepository
                .indexedCache(keyType, valueType, cacheName)
                .forObject(gradle)
                .open();
    }

    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Class<K> keyType, Class<V> valueType, Serializer<V> valueSerializer) {
        return cacheRepository
                .indexedCache(keyType, valueType, cacheName)
                .forObject(gradle)
                .withSerializer(valueSerializer)
                .open();
    }
}