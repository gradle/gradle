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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NullMarked
class PayloadHeaderSerializer {
    private final ClassLoaderSpecSerializer specSerializer = new ClassLoaderSpecSerializer();

    void write(Encoder encoder, @Nullable Object header) throws Exception {
        if (header == null) {
            encoder.writeBoolean(false);
            return;
        }
        encoder.writeBoolean(true);
        Map<Short, ClassLoaderDetails> classLoaders = asClassLoaderMap(header);
        encoder.writeSmallInt(classLoaders.size());
        for (Map.Entry<Short, ClassLoaderDetails> entry : classLoaders.entrySet()) {
            encoder.writeSmallInt(entry.getKey());
            writeDetails(encoder, entry.getValue());
        }
    }

    @Nullable Object read(Decoder decoder) throws Exception {
        if (!decoder.readBoolean()) {
            return null;
        }
        int size = decoder.readSmallInt();
        Map<Short, ClassLoaderDetails> classLoaders = new HashMap<Short, ClassLoaderDetails>(size);
        for (int i = 0; i < size; i++) {
            short id = (short) decoder.readSmallInt();
            classLoaders.put(id, readDetails(decoder));
        }
        return classLoaders;
    }

    private void writeDetails(Encoder encoder, ClassLoaderDetails details) throws Exception {
        encoder.writeLong(details.uuid.getMostSignificantBits());
        encoder.writeLong(details.uuid.getLeastSignificantBits());
        specSerializer.write(encoder, details.spec);
        encoder.writeSmallInt(details.parents.size());
        for (ClassLoaderDetails parent : details.parents) {
            writeDetails(encoder, parent);
        }
    }

    private ClassLoaderDetails readDetails(Decoder decoder) throws Exception {
        UUID uuid = new UUID(decoder.readLong(), decoder.readLong());
        ClassLoaderDetails details = new ClassLoaderDetails(uuid, specSerializer.read(decoder));
        int parentCount = decoder.readSmallInt();
        for (int i = 0; i < parentCount; i++) {
            details.parents.add(readDetails(decoder));
        }
        return details;
    }

    @SuppressWarnings("unchecked")
    private static Map<Short, ClassLoaderDetails> asClassLoaderMap(Object header) {
        if (!(header instanceof Map)) {
            throw new IllegalArgumentException("Unexpected payload header of type " + header.getClass().getName());
        }
        return (Map<Short, ClassLoaderDetails>) header;
    }
}
