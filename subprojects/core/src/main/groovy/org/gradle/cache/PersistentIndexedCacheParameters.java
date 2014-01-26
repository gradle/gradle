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
package org.gradle.cache;

import org.gradle.api.Nullable;
import org.gradle.cache.internal.CacheDecorator;
import org.gradle.messaging.serialize.BaseSerializerFactory;
import org.gradle.messaging.serialize.Serializer;

public class PersistentIndexedCacheParameters<K, V> {
    private static final BaseSerializerFactory SERIALIZER_FACTORY = new BaseSerializerFactory();
    private final String cacheName;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private CacheDecorator cacheDecorator;

    public PersistentIndexedCacheParameters(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheName = cacheName;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    public PersistentIndexedCacheParameters(String cacheName, Class<K> keyType, Serializer<V> valueSerializer) {
        this(cacheName, SERIALIZER_FACTORY.getSerializerFor(keyType), valueSerializer);
    }

    public PersistentIndexedCacheParameters(String cacheName, Class<K> keyType, Class<V> valueType) {
        this(cacheName, keyType, SERIALIZER_FACTORY.getSerializerFor(valueType));
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
    public CacheDecorator getCacheDecorator() {
        return cacheDecorator;
    }

    public PersistentIndexedCacheParameters<K, V> cacheDecorator(CacheDecorator cacheDecorator) {
        assert cacheDecorator != null;
        this.cacheDecorator = cacheDecorator;
        return this;
    }
}