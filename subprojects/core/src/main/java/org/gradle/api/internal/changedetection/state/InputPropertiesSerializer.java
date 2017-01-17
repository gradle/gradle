/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.hash.HashCode;
import org.gradle.api.GradleException;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;

import java.util.Map;

import static java.lang.String.format;

class InputPropertiesSerializer implements Serializer<Map<String, InputProperty>> {

    private final MapSerializer<String, InputProperty> serializer;

    InputPropertiesSerializer() {
        this.serializer = new MapSerializer<String, InputProperty>(BaseSerializerFactory.STRING_SERIALIZER, new InputPropertySerializer());
    }

    public Map<String, InputProperty> read(Decoder decoder) throws Exception {
        return serializer.read(decoder);
    }

    public void write(Encoder encoder, Map<String, InputProperty> properties) throws Exception {
        try {
            serializer.write(encoder, properties);
        } catch (MapSerializer.EntrySerializationException e) {
            throw new GradleException(format("Unable to store task input properties. Property '%s' with value '%s' cannot be serialized.", e.getKey(), e.getValue()), e);
        }
    }

    private static class InputPropertySerializer implements Serializer<InputProperty> {
        private static final Serializer<byte[]> SERIALIZER = BaseSerializerFactory.BYTE_ARRAY_SERIALIZER;

        @Override
        public InputProperty read(Decoder decoder) throws Exception {
            byte[] serializedBytes = SERIALIZER.read(decoder);
            HashCode hash = HashCode.fromBytes(SERIALIZER.read(decoder));

            return new DefaultInputProperty(serializedBytes, hash);
        }

        @Override
        public void write(Encoder encoder, InputProperty value) throws Exception {
            SERIALIZER.write(encoder, value.getSerializedBytes());
            SERIALIZER.write(encoder, value.getHash().asBytes());
        }
    }
}
