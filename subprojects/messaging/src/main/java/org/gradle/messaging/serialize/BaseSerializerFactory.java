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

package org.gradle.messaging.serialize;

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Map;

public class BaseSerializerFactory {
    public static final Serializer<String> STRING_SERIALIZER = new StringSerializer();
    public static final Serializer<Long> LONG_SERIALIZER = new LongSerializer();
    public static final Serializer<File> FILE_SERIALIZER = new FileSerializer();
    public static final Serializer<byte[]> BYTE_ARRAY_SERIALIZER = new ByteArraySerializer();
    public static final Serializer<Map<String, String>> NO_NULL_STRING_MAP_SERIALIZER = new StringMapSerializer();

    public <T> Serializer<T> getSerializerFor(Class<T> type) {
        if (type.equals(String.class)) {
            return (Serializer<T>) STRING_SERIALIZER;
        }
        if (type.equals(Long.class)) {
            return (Serializer) LONG_SERIALIZER;
        }
        if (type.equals(File.class)) {
            return (Serializer) FILE_SERIALIZER;
        }
        if (type.equals(byte[].class)) {
            return (Serializer) BYTE_ARRAY_SERIALIZER;
        }
        if (type.isEnum()) {
            return new EnumSerializer(type);
        }
        return new DefaultSerializer<T>(type.getClassLoader());
    }

    private static class EnumSerializer<T extends Enum> implements Serializer<T> {
        private final Class<T> type;

        private EnumSerializer(Class<T> type) {
            this.type = type;
        }

        public T read(Decoder decoder) throws Exception {
            return type.getEnumConstants()[decoder.readSmallInt()];
        }

        public void write(Encoder encoder, T value) throws Exception {
            encoder.writeSmallInt((byte) value.ordinal());
        }
    }

    private static class LongSerializer implements Serializer<Long> {
        public Long read(Decoder decoder) throws Exception {
            return decoder.readLong();
        }

        public void write(Encoder encoder, Long value) throws Exception {
            encoder.writeLong(value);
        }
    }

    private static class StringSerializer implements Serializer<String> {
        public String read(Decoder decoder) throws Exception {
            return decoder.readString();
        }

        public void write(Encoder encoder, String value) throws Exception {
            encoder.writeString(value);
        }
    }

    private static class FileSerializer implements Serializer<File> {
        public File read(Decoder decoder) throws Exception {
            return new File(decoder.readString());
        }

        public void write(Encoder encoder, File value) throws Exception {
            encoder.writeString(value.getPath());
        }
    }

    private static class ByteArraySerializer implements Serializer<byte[]> {
        public byte[] read(Decoder decoder) throws Exception {
            return decoder.readBinary();
        }

        public void write(Encoder encoder, byte[] value) throws Exception {
            encoder.writeBinary(value);
        }
    }

    private static class StringMapSerializer implements Serializer<Map<String, String>> {
        public Map<String, String> read(Decoder decoder) throws Exception {
            int pairs = decoder.readSmallInt();
            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
            for (int i = 0; i < pairs; ++i) {
                builder.put(decoder.readString(), decoder.readString());
            }
            return builder.build();
        }

        public void write(Encoder encoder, Map<String, String> value) throws Exception {
            encoder.writeSmallInt(value.size());
            for (Map.Entry<String, String> entry : value.entrySet()) {
                encoder.writeString(entry.getKey());
                encoder.writeString(entry.getValue());
            }
        }
    }
}
