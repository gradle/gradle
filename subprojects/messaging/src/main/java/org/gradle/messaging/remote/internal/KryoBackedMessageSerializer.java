/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.messaging.remote.internal;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.ObjectReader;
import org.gradle.internal.serialize.ObjectWriter;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.serialize.StatefulSerializer;
import org.gradle.messaging.remote.Address;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A message serializer that uses Kryo to perform encoding/decoding
 * @param <T>
 */
public class KryoBackedMessageSerializer<T> implements MessageSerializer<T> {
    private final StatefulSerializer<T> payloadSerializer;

    public KryoBackedMessageSerializer(StatefulSerializer<T> payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public ObjectReader<T> newReader(InputStream inputStream, Address localAddress, Address remoteAddress) {
        Decoder decoder = new KryoBackedDecoder(inputStream);
        return payloadSerializer.newReader(decoder);
    }

    @Override
    public ObjectWriter<T> newWriter(OutputStream outputStream) {
        final FlushableEncoder encoder = new KryoBackedEncoder(outputStream);
        final ObjectWriter<T> writer = payloadSerializer.newWriter(encoder);
        return new ObjectWriter<T>() {
            @Override
            public void write(T value) throws Exception {
                writer.write(value);
                encoder.flush();
            }
        };
    }
}
