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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.GradleException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

class InputPropertiesSerializer implements Serializer<ImmutableMap<String, ValueSnapshot>> {
    private final DefaultSerializer<Object> serializer;

    InputPropertiesSerializer(ClassLoader classloader) {
        this.serializer = new DefaultSerializer<Object>(classloader);
    }

    public ImmutableMap<String, ValueSnapshot> read(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableMap.of();
        }
        if (size == 1) {
            return ImmutableMap.of(decoder.readString(), (ValueSnapshot) new DefaultValueSnapshot(serializer.read(decoder)));
        }

        ImmutableMap.Builder<String, ValueSnapshot> builder = ImmutableMap.builder();
        for(int i = 0; i < size; i++) {
            builder.put(decoder.readString(), new DefaultValueSnapshot(serializer.read(decoder)));
        }
        return builder.build();
    }

    public void write(Encoder encoder, ImmutableMap<String, ValueSnapshot> properties) throws Exception {
        encoder.writeSmallInt(properties.size());
        for (Map.Entry<String, ValueSnapshot> entry : properties.entrySet()) {
            encoder.writeString(entry.getKey());
            try {
                serializer.write(encoder, entry.getValue().getValue());
            } catch (IOException e) {
                throw new GradleException(String.format("Unable to store task input properties. Property '%s' with value '%s' cannot be serialized.", entry.getKey(), entry.getValue().getValue()), e);
            }
        }
    }
}
