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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.NonNullApi;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshottingException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;

@NonNullApi
public class ArrayOfPrimitiveValueSnapshot implements ValueSnapshot, Isolatable<Object> {
    private final PrimitiveType primitiveType;
    private final Object array;

    public ArrayOfPrimitiveValueSnapshot(Object array) {
        this(PrimitiveType.of(array.getClass()), array);
    }

    private ArrayOfPrimitiveValueSnapshot(PrimitiveType primitiveType, Object array) {
        this.primitiveType = primitiveType;
        this.array = array;
    }

    public static ArrayOfPrimitiveValueSnapshot decode(Decoder decoder) throws IOException {
        byte primitiveTypeCode = decoder.readByte();
        PrimitiveType primitiveType = PrimitiveType.fromOrdinal(primitiveTypeCode);
        return new ArrayOfPrimitiveValueSnapshot(primitiveType, primitiveType.decode(decoder));
    }

    public void encode(Encoder encoder) throws IOException {
        encoder.writeByte(getPrimitiveTypeCode());
        primitiveType.encode(encoder, array);
    }

    @Override
    public int hashCode() {
        return primitiveType.hashCode(array);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ArrayOfPrimitiveValueSnapshot other = (ArrayOfPrimitiveValueSnapshot) obj;
        return primitiveType == other.primitiveType
            && primitiveType.equals(array, other.array);
    }

    @Override
    public String toString() {
        return primitiveType.toString(array);
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        // TODO: when this would be needed?
        throw new UnsupportedOperationException("This operation is not supported on primitive arrays of type: " + primitiveType.arrayType);
    }

    public ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter) {
        PrimitiveType valueType = PrimitiveType.maybeOfValue(value);
        if (primitiveType == valueType && primitiveType.equals(array, value)) {
            return this;
        }
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (snapshot.equals(this)) {
            return this;
        }
        return snapshot;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return this;
    }

    @Override
    public Object isolate() {
        return primitiveType.clone(array);
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }

    private byte getPrimitiveTypeCode() {
        return (byte) primitiveType.ordinal();
    }

    /**
     * The primitive array type.
     * <br>
     * <b>IMPORTANT: the order in which the enums are defined here matters and should be considered part of the
     * cross-version protocol since the {@link Enum#ordinal()} is part of the
     * {@link #getPrimitiveTypeCode() serialized state}.</b>
     */
    private enum PrimitiveType {
        B(byte[].class) {
            @Override
            public Object clone(Object array) {
                return ((byte[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((byte[]) x, (byte[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((byte[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((byte[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                byte[] typed = (byte[]) array;
                encoder.writeInt(typed.length);
                encoder.writeBytes(typed);
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                byte[] array = new byte[length];
                decoder.readBytes(array);
                return array;
            }
        },
        S(short[].class) {
            @Override
            public Object clone(Object array) {
                return ((short[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((short[]) x, (short[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((short[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((short[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                short[] typed = (short[]) array;
                encoder.writeInt(typed.length);
                for (short e : typed) {
                    encoder.writeInt(e);
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                short[] array = new short[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = (short) decoder.readInt();
                }
                return array;
            }
        },
        I(int[].class) {
            @Override
            public Object clone(Object array) {
                return ((int[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((int[]) x, (int[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((int[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((int[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                int[] typed = (int[]) array;
                encoder.writeInt(typed.length);
                for (int e : typed) {
                    encoder.writeInt(e);
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                int[] array = new int[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = decoder.readInt();
                }
                return array;
            }
        },
        J(long[].class) {
            @Override
            public Object clone(Object array) {
                return ((long[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((long[]) x, (long[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((long[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((long[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                long[] typed = (long[]) array;
                encoder.writeInt(typed.length);
                for (long e : typed) {
                    encoder.writeLong(e);
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                long[] array = new long[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = decoder.readLong();
                }
                return array;
            }
        },
        F(float[].class) {
            @Override
            public Object clone(Object array) {
                return ((float[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((float[]) x, (float[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((float[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((float[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                float[] typed = (float[]) array;
                encoder.writeInt(typed.length);
                for (float e : typed) {
                    encoder.writeInt(Float.floatToRawIntBits(e));
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                float[] array = new float[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = Float.intBitsToFloat(decoder.readInt());
                }
                return array;
            }
        },
        D(double[].class) {
            @Override
            public Object clone(Object array) {
                return ((double[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((double[]) x, (double[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((double[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((double[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                double[] typed = (double[]) array;
                encoder.writeInt(typed.length);
                for (double e : typed) {
                    encoder.writeLong(Double.doubleToRawLongBits(e));
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                double[] array = new double[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = Double.longBitsToDouble(decoder.readLong());
                }
                return array;
            }
        },
        C(char[].class) {
            @Override
            public Object clone(Object array) {
                return ((char[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((char[]) x, (char[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((char[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((char[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                char[] typed = (char[]) array;
                encoder.writeInt(typed.length);
                for (char e : typed) {
                    encoder.writeInt(e);
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                char[] array = new char[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = (char) decoder.readInt();
                }
                return array;
            }
        },
        Z(boolean[].class) {
            @Override
            public Object clone(Object array) {
                return ((boolean[]) array).clone();
            }

            @Override
            public boolean equals(Object x, Object y) {
                return Arrays.equals((boolean[]) x, (boolean[]) y);
            }

            @Override
            public int hashCode(Object array) {
                return Arrays.hashCode((boolean[]) array);
            }

            @Override
            public String toString(Object array) {
                return Arrays.toString((boolean[]) array);
            }

            @Override
            public void encode(Encoder encoder, Object array) throws IOException {
                boolean[] typed = (boolean[]) array;
                encoder.writeInt(typed.length);
                for (boolean e : typed) {
                    encoder.writeBoolean(e);
                }
            }

            @Override
            public Object decode(Decoder decoder) throws IOException {
                int length = decoder.readInt();
                boolean[] array = new boolean[length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = decoder.readBoolean();
                }
                return array;
            }
        };

        public final Class<?> arrayType;

        PrimitiveType(Class<?> arrayType) {
            this.arrayType = arrayType;
        }

        public abstract Object clone(Object array);

        public abstract boolean equals(Object x, Object y);

        public abstract int hashCode(Object array);

        public abstract String toString(Object array);

        public abstract void encode(Encoder encoder, Object array) throws IOException;

        public abstract Object decode(Decoder decoder) throws IOException;

        public static PrimitiveType of(Class<?> arrayType) {
            PrimitiveType primitiveType = maybeOf(arrayType);
            if (primitiveType == null) {
                throw new ValueSnapshottingException("Unsupported primitive array type: " + arrayType);
            }
            return primitiveType;
        }

        @Nullable
        public static PrimitiveType maybeOfValue(@Nullable Object value) {
            return value != null ? maybeOf(value.getClass()) : null;
        }

        @Nullable
        private static PrimitiveType maybeOf(Class<?> arrayType) {
            for (PrimitiveType primitiveType : values()) {
                if (primitiveType.arrayType == arrayType) {
                    return primitiveType;
                }
            }
            return null;
        }

        public static PrimitiveType fromOrdinal(byte ordinal) {
            PrimitiveType[] primitiveTypes = values();
            assert ordinal >= 0 && ordinal < primitiveTypes.length;
            return primitiveTypes[ordinal];
        }
    }

}
