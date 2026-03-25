/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializer for {@code Map<K, V>} that writes entries in sorted key order,
 * ensuring deterministic serialization regardless of input iteration order.
 *
 * <p>Keys must be {@link Comparable}. The wire format is identical to {@link MapSerializer}:
 * size followed by key/value pairs. On read, entries are returned in a {@link LinkedHashMap}
 * preserving the serialized (sorted) order.</p>
 */
@NullMarked
public class SortedMapSerializer<K extends Comparable<K>, V> extends AbstractSerializer<Map<K, V>> {

    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;

    public SortedMapSerializer(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void write(Encoder encoder, Map<K, V> value) throws Exception {
        // We cannot create a K[] directly due to type erasure; Comparable[] is the
        // closest array type available since K extends Comparable<K>.
        K[] sortedKeys = (K[]) value.keySet().toArray(new Comparable[0]);
        Arrays.sort(sortedKeys);
        encoder.writeInt(sortedKeys.length);
        for (K key : sortedKeys) {
            keySerializer.write(encoder, key);
            valueSerializer.write(encoder, value.get(key));
        }
    }

    @Override
    public Map<K, V> read(Decoder decoder) throws Exception {
        int size = decoder.readInt();
        Map<K, V> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            K key = keySerializer.read(decoder);
            V value = valueSerializer.read(decoder);
            map.put(key, value);
        }
        return map;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        SortedMapSerializer<?, ?> rhs = (SortedMapSerializer<?, ?>) obj;
        return Objects.equal(keySerializer, rhs.keySerializer)
            && Objects.equal(valueSerializer, rhs.valueSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), keySerializer, valueSerializer);
    }
}
