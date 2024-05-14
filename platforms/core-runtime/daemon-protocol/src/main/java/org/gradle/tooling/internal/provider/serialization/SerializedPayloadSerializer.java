/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializer;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.ArrayList;
import java.util.List;

public class SerializedPayloadSerializer implements Serializer<SerializedPayload> {
    private final Serializer<Object> javaSerializer = new DefaultSerializer<Object>();

    @Override
    public void write(Encoder encoder, SerializedPayload value) throws Exception {
        javaSerializer.write(encoder, value.getHeader());
        encoder.writeSmallInt(value.getSerializedModel().size());
        for (byte[] bytes : value.getSerializedModel()) {
            encoder.writeBinary(bytes);
        }
    }

    @Override
    public SerializedPayload read(Decoder decoder) throws Exception {
        Object header = javaSerializer.read(decoder);
        int count = decoder.readSmallInt();
        List<byte[]> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(decoder.readBinary());
        }
        return new SerializedPayload(header, chunks);
    }
}
