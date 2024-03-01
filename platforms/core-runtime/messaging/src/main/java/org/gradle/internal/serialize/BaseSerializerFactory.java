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
import org.gradle.internal.Cast;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class BaseSerializerFactory {
    public static final Serializer<String> STRING_SERIALIZER = new StringSerializer();
    public static final Serializer<Boolean> BOOLEAN_SERIALIZER = new BooleanSerializer();
    public static final Serializer<Byte> BYTE_SERIALIZER = new ByteSerializer();
    public static final Serializer<Character> CHAR_SERIALIZER = new CharSerializer();
    public static final Serializer<Short> SHORT_SERIALIZER = new ShortSerializer();
    public static final Serializer<Integer> INTEGER_SERIALIZER = new IntegerSerializer();
    public static final Serializer<Long> LONG_SERIALIZER = new LongSerializer();
    public static final Serializer<Float> FLOAT_SERIALIZER = new FloatSerializer();
    public static final Serializer<Double> DOUBLE_SERIALIZER = new DoubleSerializer();
    public static final Serializer<File> FILE_SERIALIZER = new FileSerializer();
    public static final Serializer<Path> PATH_SERIALIZER = new PathSerializer();
    public static final Serializer<byte[]> BYTE_ARRAY_SERIALIZER = new ByteArraySerializer();
    public static final Serializer<Map<String, String>> NO_NULL_STRING_MAP_SERIALIZER = new StringMapSerializer();
    public static final Serializer<Throwable> THROWABLE_SERIALIZER = new ThrowableSerializer();
    public static final Serializer<HashCode> HASHCODE_SERIALIZER = new HashCodeSerializer();
    public static final Serializer<BigInteger> BIG_INTEGER_SERIALIZER = new BigIntegerSerializer();
    public static final Serializer<BigDecimal> BIG_DECIMAL_SERIALIZER = new BigDecimalSerializer();

    public <T> Serializer<T> getSerializerFor(Class<T> type) {
        if (type.equals(String.class)) {
            return Cast.uncheckedNonnullCast(STRING_SERIALIZER);
        }
        if (type.equals(Long.class)) {
            return Cast.uncheckedNonnullCast(LONG_SERIALIZER);
        }
        if (type.equals(File.class)) {
            return Cast.uncheckedNonnullCast(FILE_SERIALIZER);
        }
        if (type.equals(byte[].class)) {
            return Cast.uncheckedNonnullCast(BYTE_ARRAY_SERIALIZER);
        }
        if (type.isEnum()) {
            return Cast.uncheckedNonnullCast(new EnumSerializer<Enum<?>>(Cast.<Class<Enum<?>>>uncheckedNonnullCast(type)));
        }
        if (type.equals(Boolean.class)) {
            return Cast.uncheckedNonnullCast(BOOLEAN_SERIALIZER);
        }
        if (Throwable.class.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast(THROWABLE_SERIALIZER);
        }
        if (HashCode.class.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast(HASHCODE_SERIALIZER);
        }
        if (Path.class.isAssignableFrom(type)) {
            return Cast.uncheckedNonnullCast(PATH_SERIALIZER);
        }
        return new DefaultSerializer<T>(type.getClassLoader());
    }

    private static class EnumSerializer<T extends Enum<?>> extends AbstractSerializer<T> {
        private final Class<T> type;

        private EnumSerializer(Class<T> type) {
            this.type = type;
        }

        @Override
        public T read(Decoder decoder) throws Exception {
            return type.getEnumConstants()[decoder.readSmallInt()];
        }

        @Override
        public void write(Encoder encoder, T value) throws Exception {
            encoder.writeSmallInt((byte) value.ordinal());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            EnumSerializer<?> rhs = (EnumSerializer<?>) obj;
            return Objects.equal(type, rhs.type);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), type);
        }
    }

    private static class LongSerializer extends AbstractSerializer<Long> {
        @Override
        public Long read(Decoder decoder) throws Exception {
            return decoder.readLong();
        }

        @Override
        public void write(Encoder encoder, Long value) throws Exception {
            encoder.writeLong(value);
        }
    }

    private static class StringSerializer extends AbstractSerializer<String> {
        @Override
        public String read(Decoder decoder) throws Exception {
            return decoder.readString();
        }

        @Override
        public void write(Encoder encoder, String value) throws Exception {
            encoder.writeString(value);
        }
    }

    private static class FileSerializer extends AbstractSerializer<File> {
        @Override
        public File read(Decoder decoder) throws Exception {
            return new File(decoder.readString());
        }

        @Override
        public void write(Encoder encoder, File value) throws Exception {
            encoder.writeString(value.getPath());
        }
    }

    private static class PathSerializer extends AbstractSerializer<Path> {
        @Override
        public Path read(Decoder decoder) throws Exception {
            return Paths.get(decoder.readString());
        }

        @Override
        public void write(Encoder encoder, Path value) throws Exception {
            encoder.writeString(value.toString());
        }
    }

    private static class ByteArraySerializer extends AbstractSerializer<byte[]> {
        @Override
        public byte[] read(Decoder decoder) throws Exception {
            return decoder.readBinary();
        }

        @Override
        public void write(Encoder encoder, byte[] value) throws Exception {
            encoder.writeBinary(value);
        }
    }

    private static class StringMapSerializer extends AbstractSerializer<Map<String, String>> {
        @Override
        public Map<String, String> read(Decoder decoder) throws Exception {
            int pairs = decoder.readSmallInt();
            ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
            for (int i = 0; i < pairs; ++i) {
                builder.put(decoder.readString(), decoder.readString());
            }
            return builder.build();
        }

        @Override
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
            return decoder.readShort();
        }

        @Override
        public void write(Encoder encoder, Short value) throws Exception {
            encoder.writeShort(value);
        }
    }

    private static class CharSerializer extends AbstractSerializer<Character> {
        @Override
        public Character read(Decoder decoder) throws Exception {
            return (char) decoder.readInt();
        }

        @Override
        public void write(Encoder encoder, Character value) throws Exception {
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
            return decoder.readFloat();
        }

        @Override
        public void write(Encoder encoder, Float value) throws Exception {
            encoder.writeFloat(value);
        }
    }

    private static class DoubleSerializer extends AbstractSerializer<Double> {
        @Override
        public Double read(Decoder decoder) throws Exception {
            return decoder.readDouble();
        }

        @Override
        public void write(Encoder encoder, Double value) throws Exception {
            encoder.writeDouble(value);
        }
    }

    private static class BigIntegerSerializer extends AbstractSerializer<BigInteger> {
        @Override
        public BigInteger read(Decoder decoder) throws Exception {
            return new BigInteger(decoder.readBinary());
        }

        @Override
        public void write(Encoder encoder, BigInteger value) throws Exception {
            encoder.writeBinary(value.toByteArray());
        }
    }

    private static class BigDecimalSerializer extends AbstractSerializer<BigDecimal> {
        @Override
        public BigDecimal read(Decoder decoder) throws Exception {
            BigInteger unscaledVal = BIG_INTEGER_SERIALIZER.read(decoder);
            int scale = decoder.readSmallInt();
            return new BigDecimal(unscaledVal, scale);
        }

        @Override
        public void write(Encoder encoder, BigDecimal value) throws Exception {
            BIG_INTEGER_SERIALIZER.write(encoder, value.unscaledValue());
            encoder.writeSmallInt(value.scale());
        }
    }

    private static class ThrowableSerializer extends AbstractSerializer<Throwable> {
        @Override
        public Throwable read(Decoder decoder) throws Exception {
            return (Throwable) Message.receive(decoder.getInputStream(), getClass().getClassLoader());
        }

        @Override
        public void write(Encoder encoder, Throwable value) throws Exception {
            Message.send(value, encoder.getOutputStream());
        }
    }
}
