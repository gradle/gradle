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
import org.gradle.api.file.RegularFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * Represents the grouped metadata from a single test metadata event in a binary format that can be serialized to disk.
 */
@NonNullApi
public final class SerializedMetadata {
    private final long logTime;
    private final ImmutableList<SerializedMetadataElement> metadatas;

    public SerializedMetadata(long logTime, Map<String, Object> metadata) {
        this.logTime = logTime;

        ImmutableList.Builder<SerializedMetadataElement> builder = ImmutableList.builder();
        metadata.forEach((key, value) -> builder.add(new SerializedMetadataElement(key, SerializedMetadataElement.toBytes(value), value.getClass().getName())));
        this.metadatas = builder.build();
    }

    public SerializedMetadata(long logTime, ImmutableList<SerializedMetadataElement> metadatas) {
        this.logTime = logTime;
        this.metadatas = metadatas;
    }

    public long getLogTime() {
        return logTime;
    }

    public ImmutableList<SerializedMetadataElement> getEntries() {
        return metadatas;
    }

    @NonNullApi
    public static final class SerializedMetadataElement {
        private final String key;
        private final byte[] value;
        private final String valueType;

        public SerializedMetadataElement(String key, byte[] value, String valueType) {
            this.key = key;
            this.value = value;
            this.valueType = valueType;
        }

        public String getKey() {
            return key;
        }

        public byte[] getSerializedValue() {
            return value;
        }

        public Object getValue() {
            return fromBytes(value);
        }

        public String getValueType() {
            return valueType;
        }

        // TODO: Use some other, better serialization strategy here informed by Problems API work
        // TODO: Or just use the KryoBackedEncoder/Decoder - the exact serialization strategy isn't too important
        private static byte[] toBytes(Object obj) {
            if (obj instanceof byte[]) {
                return (byte[]) obj;
            }

            // TODO: File serialization just saves the path for now
            Object toSerialize;
            if (obj instanceof File) {
                toSerialize = ((File) obj).toURI();
            } else if (obj instanceof RegularFile) {
                toSerialize = ((RegularFile) obj).getAsFile().toURI();
            } else {
                toSerialize = obj;
            }

            if (toSerialize instanceof Serializable) {
                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                     ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
                    objectStream.writeObject(toSerialize);
                    return byteStream.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize metadata entry: " + toSerialize, e);
                }
            } else {
                throw new IllegalArgumentException("Object must be Serializable");
            }
        }

        private Object fromBytes(byte[] bytes) {
            try (ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
                 ObjectInputStream objectStream = new ObjectInputStream(byteStream)) {
                return objectStream.readObject();
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize metadata entry: " + key, e);
            }
        }
    }
}
