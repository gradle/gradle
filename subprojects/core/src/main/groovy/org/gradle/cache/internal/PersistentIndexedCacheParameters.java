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

import org.gradle.api.Nullable;
import org.gradle.api.internal.changedetection.state.InMemoryPersistentCacheDecorator;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.messaging.serialize.Serializer;

public class PersistentIndexedCacheParameters<K, V> {
    private final String cacheName;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private InMemoryPersistentCacheDecorator cacheDecorator;

    public PersistentIndexedCacheParameters(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheName = cacheName;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    public PersistentIndexedCacheParameters(String cacheName, Class<K> keyType, Serializer<V> valueSerializer) {
        this(cacheName, new DefaultSerializer<K>(keyType.getClassLoader()), valueSerializer);
    }

    public PersistentIndexedCacheParameters(String cacheName, Class<K> keyType, Class<V> valueType) {
        this(cacheName, keyType, new DefaultSerializer<V>(valueType.getClassLoader()));
    }

    public String getCacheName() {
        return cacheName;
    }

    public Serializer<K> getKeySerializer() {
        return keySerializer;
    }

    public Serializer<V> getValueSerializer() {
        return valueSerializer;
    }

    @Nullable
    public InMemoryPersistentCacheDecorator getCacheDecorator() {
        return cacheDecorator;
    }

    public PersistentIndexedCacheParameters<K, V> cacheDecorator(InMemoryPersistentCacheDecorator cacheDecorator) {
        assert cacheDecorator != null;
        this.cacheDecorator = cacheDecorator;
        return this;
    }
}