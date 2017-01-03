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

package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Map;

public class BaseSerializerFactory {
    public static final Serializer<String> STRING_SERIALIZER = new StringSerializer();
    public static final Serializer<Boolean> BOOLEAN_SERIALIZER = new BooleanSerializer();
    public static final Serializer<Byte> BYTE_SERIALIZER = new ByteSerializer();
    public static final Serializer<Short> SHORT_SERIALIZER = new ShortSerializer();
    public static final Serializer<Integer> INTEGER_SERIALIZER = new IntegerSerializer();
    public static final Serializer<Long> LONG_SERIALIZER = new LongSerializer();
    public static final Serializer<Float> FLOAT_SERIALIZER = new FloatSerializer();
    public static final Serializer<Double> DOUBLE_SERIALIZER = new DoubleSerializer();
    public static final Serializer<File> FILE_SERIALIZER = new FileSerializer();
    public static final Serializer<byte[]> BYTE_ARRAY_SERIALIZER = new ByteArraySerializer();
    public static final Serializer<Map<String, String>> NO_NULL_STRING_MAP_SERIALIZER = new StringMapSerializer();
    public static final Serializer<Throwable> THROWABLE_SERIALIZER = new ThrowableSerializer();

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
        if (type.equals(Boolean.class)) {
            return (Serializer<T>) BOOLEAN_SERIALIZER;
        }
        if (Throwable.class.isAssignableFrom(type)) {
            return (Serializer<T>) THROWABLE_SERIALIZER;
        }
        return new DefaultSerializer<T>(type.getClassLoader());
    }

    private static class EnumSerializer<T extends Enum> extends AbstractSerializer<T> {
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

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            EnumSerializer rhs = (EnumSerializer) obj;
            return Objects.equal(type, rhs.type);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), type);
        }
    }

    private static class LongSerializer extends AbstractSerializer<Long> {
        public Long read(Decoder decoder) throws Exception {
            return decoder.readLong();
        }

        public void write(Encoder encoder, Long value) throws Exception {
            encoder.writeLong(value);
        }
    }

    private static class StringSerializer extends AbstractSerializer<String> {
        public String read(Decoder decoder) throws Exception {
            return decoder.readString();
        }

        public void write(Encoder encoder, String value) throws Exception {
            encoder.writeString(value);
        }
    }

    private static class FileSerializer extends AbstractSerializer<File> {
        public File read(Decoder decoder) throws Exception {
            return new File(decoder.readString());
        }

        public void write(Encoder encoder, File value) throws Exception {
            encoder.writeString(value.getPath());
        }
    }

    private static class ByteArraySerializer extends AbstractSerializer<byte[]> {
        public byte[] read(Decoder decoder) throws Exception {
            return decoder.readBinary();
        }

        public void write(Encoder encoder, byte[] value) throws Exception {
            encoder.writeBinary(value);
        }
    }

    private static class StringMapSerializer extends AbstractSerializer<Map<String, String>> {
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

    private static class BooleanSerializer extends AbstractSerializer<Boolean> {
        @Override
        public Boolean read(Decoder decoder) throws Exception {
            return decoder.readBoolean();
        }

        @Override
        public void write(Encoder encoder, Boolean value) throws Exception {
            encoder.writeBoolean(value);
        }
    }

    private static class ByteSerializer extends AbstractSerializer<Byte> {
        @Override
        public Byte read(Decoder decoder) throws Exception {
            return decoder.readByte();
        }

        @Override
        public void write(Encoder encoder, Byte value) throws Exception {
            encoder.writeByte(value);
        }
    }

    private static class ShortSerializer extends AbstractSerializer<Short> {
        @Override
        public Short read(Decoder decoder) throws Exception {
            return (short) decoder.readInt();
        }

        @Override
        public void write(Encoder encoder, Short value) throws Exception {
            encoder.writeInt(value);
        }
    }

    private static class IntegerSerializer extends AbstractSerializer<Integer> {
        @Override
        public Integer read(Decoder decoder) throws Exception {
            return decoder.readInt();
        }

        @Override
        public void write(Encoder encoder, Integer value) throws Exception {
            encoder.writeInt(value);
        }
    }

    private static class FloatSerializer extends AbstractSerializer<Float> {
        @Override
        public Float read(Decoder decoder) throws Exception {
            byte[] bytes = new byte[4];
            decoder.readBytes(bytes);
            return ByteBuffer.wrap(bytes).getFloat();
        }

        @Override
        public void write(Encoder encoder, Float value) throws Exception {
            byte[] b = ByteBuffer.allocate(4).putFloat(value).array();
            encoder.writeBytes(b);
        }
    }

    private static class DoubleSerializer extends AbstractSerializer<Double> {
        @Override
        public Double read(Decoder decoder) throws Exception {
            byte[] bytes = new byte[8];
            decoder.readBytes(bytes);
            return ByteBuffer.wrap(bytes).getDouble();
        }

        @Override
        public void write(Encoder encoder, Double value) throws Exception {
            byte[] b = ByteBuffer.allocate(8).putDouble(value).array();
            encoder.writeBytes(b);
        }
    }

    private static class ThrowableSerializer extends AbstractSerializer<Throwable> {
        public Throwable read(Decoder decoder) throws Exception {
            return (Throwable) Message.receive(decoder.getInputStream(), getClass().getClassLoader());
        }

        public void write(Encoder encoder, Throwable value) throws Exception {
            Message.send(value, encoder.getOutputStream());
        }
    }
}
