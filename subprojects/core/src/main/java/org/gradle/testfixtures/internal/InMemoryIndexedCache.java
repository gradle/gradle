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
package org.gradle.testfixtures.internal;

import org.gradle.api.Transformer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A simple in-memory cache, used by the testing fixtures.
 */
public class InMemoryIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private final Map<Object, byte[]> entries = new ConcurrentHashMap<Object, byte[]>();
    private final ProducerGuard<K> producerGuard = ProducerGuard.serial();
    private final Serializer<V> valueSerializer;

    public InMemoryIndexedCache(Serializer<V> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public V get(K key) {
        byte[] serialised = entries.get(key);
        if (serialised == null) {
            return null;
        }
        try {
            ByteArrayInputStream instr = new ByteArrayInputStream(serialised);
            InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(instr);
            return valueSerializer.read(decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public V get(final K key, final Transformer<? extends V, ? super K> producer) {
        return producerGuard.guardByKey(key, new Supplier<V>() {
            @Override
            public V get() {
                if (!entries.containsKey(key)) {
                    put(key, producer.transform(key));
                }
                return InMemoryIndexedCache.this.get(key);
            }
        });
    }

    @Override
    public void put(K key, V value) {
        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(outstr);
        try {
            valueSerializer.write(encoder, value);
            encoder.flush();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        entries.put(key, outstr.toByteArray());
    }

    @Override
    public void remove(K key) {
        entries.remove(key);
    }

    @SuppressWarnings("unchecked")
    public Set<K> keySet() {
        return (Set<K>) entries.keySet();
    }
}
