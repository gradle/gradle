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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.serialize.InputStreamBackedDecoder;
import org.gradle.messaging.serialize.OutputStreamBackedEncoder;
import org.gradle.messaging.serialize.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple in-memory cache, used by the testing fixtures.
 */
public class InMemoryIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private final Map<Object, byte[]> entries = new HashMap<Object, byte[]>();
    private final Serializer<V> valueSerializer;

    public InMemoryIndexedCache(Serializer<V> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

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

    public void remove(K key) {
        entries.remove(key);
    }
}
