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
import org.gradle.internal.hash.Hashable;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshottingException;

import javax.annotation.Nullable;

import static org.gradle.internal.snapshot.impl.AbstractValueProcessor.javaSerialized;
import static org.gradle.internal.snapshot.impl.JavaSerializedValueSnapshot.javaDeserialized;

@NonNullApi
public class IsolatedArrayOfPrimitive implements Isolatable<Object>, Hashable {
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
        return new UnsupportedOperationException("This operation is not supported on primitive arrays of type: " + primitiveType.getArrayClass());
    }

    @Override
    public Object isolate() {
        switch (primitiveType) {
            case B:
                return ((byte[]) array).clone();
            case S:
                return ((short[]) array).clone();
            case I:
                return ((int[]) array).clone();
            case J:
                return ((long[]) array).clone();
            case F:
                return ((float[]) array).clone();
            case D:
                return ((double[]) array).clone();
            case C:
                return ((char[]) array).clone();
        }
        throw new IllegalStateException();
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
                return javaDeserialized(primitiveType.getArrayClass(), bytes);
        }
    }

    private enum PrimitiveType {
        B,
        S,
        I,
        J,
        F,
        D,
        C;

        public static PrimitiveType of(Class<?> arrayType) {
            if (byte[].class == arrayType) {
                return B;
            }
            if (short[].class == arrayType) {
                return S;
            }
            if (int[].class == arrayType) {
                return I;
            }
            if (long[].class == arrayType) {
                return J;
            }
            if (float[].class == arrayType) {
                return F;
            }
            if (double[].class == arrayType) {
                return D;
            }
            if (char[].class == arrayType) {
                return C;
            }
            throw new ValueSnapshottingException("Unsupported primitive array type: " + arrayType);
        }

        public static PrimitiveType fromOrdinal(byte ordinal) {
            PrimitiveType[] primitiveTypes = values();
            assert ordinal >= 0 && ordinal < primitiveTypes.length;
            return primitiveTypes[ordinal];
        }

        public Class<?> getArrayClass() {
            switch (this) {
                case B:
                    return byte[].class;
                case S:
                    return short[].class;
                case I:
                    return int[].class;
                case J:
                    return long[].class;
                case F:
                    return float[].class;
                case D:
                    return double[].class;
                case C:
                    return char[].class;
            }
            throw new IllegalStateException();
        }
    }
}
