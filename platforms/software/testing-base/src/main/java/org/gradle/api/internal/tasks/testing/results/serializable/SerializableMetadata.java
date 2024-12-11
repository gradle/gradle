/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results.serializable;

import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * Represents the grouped metadata from a single test metadata event in a binary format that can be serialized to disk.
 */
@NonNullApi
public final class SerializableMetadata {
    private final long logTime;
    private final ImmutableList<SerializableMetadataEntry> metadatas;

    public SerializableMetadata(long logTime, Map<String, Object> metadata) {
        this.logTime = logTime;

        ImmutableList.Builder<SerializableMetadataEntry> builder = ImmutableList.builder();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            try {
                builder.add(new SerializableMetadataEntry(entry.getKey(), SerializableMetadataEntry.toBytes(entry.getValue()), entry.getValue().getClass().getName()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize metadata entry: " + entry.getKey(), e);
            }
        }
        this.metadatas = builder.build();
    }

    public SerializableMetadata(long logTime, ImmutableList<SerializableMetadataEntry> metadatas) {
        this.logTime = logTime;
        this.metadatas = metadatas;
    }

    public long getLogTime() {
        return logTime;
    }

    public ImmutableList<SerializableMetadataEntry> getEntries() {
        return metadatas;
    }

    @NonNullApi
    public static final class SerializableMetadataEntry {
        private final String key;
        private final byte[] value;
        private final String valueType;

        public SerializableMetadataEntry(String key, byte[] value, String valueType) {
            this.key = key;
            this.value = value;
            this.valueType = valueType;
        }

        // TODO: Use some other, better serialization strategy here informed by Problems API work
        // TODO: Or just use the KryoBackedEncoder/Decoder - the exact serialization strategy isn't too important
        private static byte[] toBytes(Object obj) throws IOException {
            if (obj instanceof byte[]) {
                return (byte[]) obj;
            } else if (obj instanceof Serializable) {
                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                     ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
                    objectStream.writeObject(obj);
                    return byteStream.toByteArray();
                }
            } else {
                throw new IllegalArgumentException("Object must be Serializable");
            }
        }

        @Override
        public String toString() {
            return "SerializableMetadataEntry{" +
                "key='" + key + '\'' +
                ", value=" + Arrays.toString(value) +
                ", valueType='" + valueType + '\'' +
                '}';
        }

        public String getKey() {
            return key;
        }

        public byte[] getValue() {
            return value;
        }

        public String getValueType() {
            return valueType;
        }
    }
}
