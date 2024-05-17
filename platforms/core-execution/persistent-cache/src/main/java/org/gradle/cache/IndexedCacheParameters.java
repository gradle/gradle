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

import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;

public class IndexedCacheParameters<K, V> {
    private static final BaseSerializerFactory SERIALIZER_FACTORY = new BaseSerializerFactory();

    private final String cacheName;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private final CacheDecorator cacheDecorator;

    public static <K, V> IndexedCacheParameters<K, V> of(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return new IndexedCacheParameters<K, V>(cacheName, keySerializer, valueSerializer, null);
    }

    public static <K, V> IndexedCacheParameters<K, V> of(String cacheName, Class<K> keyType, Serializer<V> valueSerializer) {
        return new IndexedCacheParameters<K, V>(cacheName, SERIALIZER_FACTORY.getSerializerFor(keyType), valueSerializer, null);
    }

    public static <K, V> IndexedCacheParameters<K, V> of(String cacheName, Class<K> keyType, Class<V> valueType) {
        return new IndexedCacheParameters<K, V>(cacheName, SERIALIZER_FACTORY.getSerializerFor(keyType), SERIALIZER_FACTORY.getSerializerFor(valueType), null);
    }

    private IndexedCacheParameters(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer, @Nullable CacheDecorator cacheDecorator) {
        this.cacheName = cacheName;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.cacheDecorator = cacheDecorator;
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

    public IndexedCacheParameters<K, V> withCacheDecorator(CacheDecorator cacheDecorator) {
        return new IndexedCacheParameters<K, V>(cacheName, keySerializer, valueSerializer, cacheDecorator);
    }
}
