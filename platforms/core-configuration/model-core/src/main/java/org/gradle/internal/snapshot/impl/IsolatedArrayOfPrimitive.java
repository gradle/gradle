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
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshottingException;

import javax.annotation.Nullable;

import static org.gradle.internal.snapshot.impl.AbstractValueProcessor.javaSerialized;
import static org.gradle.internal.snapshot.impl.JavaSerializedValueSnapshot.javaDeserialized;

@NonNullApi
public class IsolatedArrayOfPrimitive implements Isolatable<Object> {
    private final PrimitiveType primitiveType;
    private final Object array;

    public IsolatedArrayOfPrimitive(Object array) {
        this(PrimitiveType.of(array.getClass()), array);
    }

    private IsolatedArrayOfPrimitive(PrimitiveType primitiveType, Object array) {
        this.primitiveType = primitiveType;
        this.array = array;
    }

    public static IsolatedArrayOfPrimitive fromByteArray(byte primitiveTypeCode, byte[] bytes) {
        PrimitiveType primitiveType = PrimitiveType.fromOrdinal(primitiveTypeCode);
        return new IsolatedArrayOfPrimitive(
            primitiveType,
            fromByteArray(bytes, primitiveType)
        );
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        throw unsupportedOperation();
    }

    @Override
    public ValueSnapshot asSnapshot() {
        throw unsupportedOperation();
    }

    private UnsupportedOperationException unsupportedOperation() {
        return new UnsupportedOperationException("This operation is not supported on primitive arrays of type: " + primitiveType.arrayType);
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
        },
        S(short[].class) {
            @Override
            public Object clone(Object array) {
                return ((short[]) array).clone();
            }
        },
        I(int[].class) {
            @Override
            public Object clone(Object array) {
                return ((int[]) array).clone();
            }
        },
        J(long[].class) {
            @Override
            public Object clone(Object array) {
                return ((long[]) array).clone();
            }
        },
        F(float[].class) {
            @Override
            public Object clone(Object array) {
                return ((float[]) array).clone();
            }
        },
        D(double[].class) {
            @Override
            public Object clone(Object array) {
                return ((double[]) array).clone();
            }
        },
        C(char[].class) {
            @Override
            public Object clone(Object array) {
                return ((char[]) array).clone();
            }
        },
        Z(boolean[].class) {
            @Override
            public Object clone(Object array) {
                return ((boolean[]) array).clone();
            }
        };

        public final Class<?> arrayType;

        PrimitiveType(Class<?> arrayType) {
            this.arrayType = arrayType;
        }

        public abstract Object clone(Object array);

        public static PrimitiveType of(Class<?> arrayType) {
            for (PrimitiveType primitiveType : values()) {
                if (primitiveType.arrayType == arrayType) {
                    return primitiveType;
                }
            }
            throw new ValueSnapshottingException("Unsupported primitive array type: " + arrayType);
        }

        public static PrimitiveType fromOrdinal(byte ordinal) {
            PrimitiveType[] primitiveTypes = values();
            assert ordinal >= 0 && ordinal < primitiveTypes.length;
            return primitiveTypes[ordinal];
        }
    }
}
