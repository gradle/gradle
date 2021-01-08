/*
 * Copyright 2013 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

public class MapSerializer<U, V> extends AbstractSerializer<Map<U, V>> {
    private final Serializer<U> keySerializer;
    private final Serializer<V> valueSerializer;

    public MapSerializer(Serializer<U> keySerializer, Serializer<V> valueSerializer) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @Override
    public Map<U, V> read(Decoder decoder) throws Exception {
        int size = decoder.readInt();
        Map<U, V> valueMap = new LinkedHashMap<U, V>(size);
        for (int i = 0; i < size; i++) {
            U key = keySerializer.read(decoder);
            V value = valueSerializer.read(decoder);
            valueMap.put(key, value);
        }
        return valueMap;
    }

    @Override
    public void write(Encoder encoder, Map<U, V> value) throws Exception {
        encoder.writeInt(value.size());
        for (Map.Entry<U, V> entry : value.entrySet()) {
            keySerializer.write(encoder, entry.getKey());
            valueSerializer.write(encoder, entry.getValue());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        MapSerializer<?, ?> rhs = (MapSerializer<?, ?>) obj;
        return Objects.equal(keySerializer, rhs.keySerializer)
            && Objects.equal(valueSerializer, rhs.valueSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), keySerializer, valueSerializer);
    }
}
