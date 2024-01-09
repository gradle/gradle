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

import static org.gradle.internal.snapshot.impl.AbstractValueProcessor.javaSerialized;
import static org.gradle.internal.snapshot.impl.JavaSerializedValueSnapshot.javaDeserialized;

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
        byte[] bytes = readLengthPrefixedByteArray(decoder);
        return ArrayOfPrimitiveValueSnapshot.fromByteArray(primitiveTypeCode, bytes);
    }

    public void encode(Encoder encoder) throws IOException {
        encoder.writeByte(getPrimitiveTypeCode());
        writeLengthPrefixedByteArray(encoder, toByteArray());
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

    public byte getPrimitiveTypeCode() {
        return (byte) primitiveType.ordinal();
    }

    public byte[] toByteArray() {
        switch (primitiveType) {
            case B:
                return (byte[]) array;
            default:
                return javaSerialized(array);
        }
    }

    private static Object fromByteArray(byte[] bytes, PrimitiveType primitiveType) {
        switch (primitiveType) {
            case B:
                return bytes;
            default:
                return javaDeserialized(primitiveType.arrayType, bytes);
        }
    }

    private static ArrayOfPrimitiveValueSnapshot fromByteArray(byte primitiveTypeCode, byte[] bytes) {
        PrimitiveType primitiveType = PrimitiveType.fromOrdinal(primitiveTypeCode);
        return new ArrayOfPrimitiveValueSnapshot(primitiveType, fromByteArray(bytes, primitiveType));
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
        };

        public final Class<?> arrayType;

        PrimitiveType(Class<?> arrayType) {
            this.arrayType = arrayType;
        }

        public abstract Object clone(Object array);

        public abstract boolean equals(Object x, Object y);

        public abstract int hashCode(Object array);

        public abstract String toString(Object array);

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

    private static byte[] readLengthPrefixedByteArray(Decoder decoder) throws IOException {
        int length = decoder.readInt();
        byte[] bytes = new byte[length];
        decoder.readBytes(bytes);
        return bytes;
    }

    private static void writeLengthPrefixedByteArray(Encoder encoder, byte[] bytes) throws IOException {
        encoder.writeInt(bytes.length);
        encoder.writeBytes(bytes);
    }
}
