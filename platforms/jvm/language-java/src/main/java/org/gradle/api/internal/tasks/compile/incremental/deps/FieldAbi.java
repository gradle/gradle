/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.deps;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The ABI of a class file field.
 */
@NullMarked
public final class FieldAbi {

    private final int access;
    private final String descriptor;
    private final @Nullable String signature;
    private final @Nullable Object value;

    public FieldAbi(int access, String descriptor, @Nullable String signature, @Nullable Object value) {
        assert value == null || value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
        this.access = access;
        this.descriptor = descriptor;
        this.signature = signature;
        this.value = value;
    }

    public int getAccess() {
        return access;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public @Nullable String getSignature() {
        return signature;
    }

    public @Nullable Object getValue() {
        return value;
    }

    public boolean isCompatible(FieldAbi fieldAbi) {
        return access == fieldAbi.access
            && descriptor.equals(fieldAbi.descriptor)
            && Objects.equals(signature, fieldAbi.signature)
            && Objects.equals(value, fieldAbi.value);
    }

    @NullMarked
    public static class Serializer extends AbstractSerializer<FieldAbi> {

        private final StringInterner interner;

        public Serializer(StringInterner interner) {
            this.interner = interner;
        }

        @Override
        public FieldAbi read(Decoder decoder) throws Exception {
            int access = decoder.readInt();
            String descriptor = interner.intern(decoder.readString());
            String signature = interner.intern(decoder.readNullableString());
            byte kind = decoder.readByte();
            Object value;
            switch (kind) {
                case 0:
                    value = null;
                    break;
                case 1:
                    value = decoder.readString();
                    break;
                case 2:
                    value = decoder.readInt();
                    break;
                case 3:
                    value = decoder.readLong();
                    break;
                case 4:
                    value = decoder.readFloat();
                    break;
                case 5:
                    value = decoder.readDouble();
                    break;
                default:
                    throw new AssertionError("Invalid kind: " + kind);
            }
            return new FieldAbi(access, descriptor, signature, value);
        }

        @Override
        public void write(Encoder encoder, FieldAbi value) throws Exception {
            encoder.writeInt(value.getAccess());
            encoder.writeString(value.getDescriptor());
            encoder.writeNullableString(value.getSignature());
            Object fieldValue = value.getValue();
            if (fieldValue == null) {
                encoder.writeByte((byte) 0);
            } else {
                if (fieldValue instanceof String) {
                    encoder.writeByte((byte) 1);
                    encoder.writeString((String) fieldValue);
                } else if (fieldValue instanceof Integer) {
                    encoder.writeByte((byte) 2);
                    encoder.writeInt((Integer) fieldValue);
                } else if (fieldValue instanceof Long) {
                    encoder.writeByte((byte) 3);
                    encoder.writeLong((Long) fieldValue);
                } else if (fieldValue instanceof Float) {
                    encoder.writeByte((byte) 4);
                    encoder.writeFloat((Float) fieldValue);
                } else if (fieldValue instanceof Double) {
                    encoder.writeByte((byte) 5);
                    encoder.writeDouble((Double) fieldValue);
                } else {
                    throw new AssertionError("Invalid default value: " + fieldValue.getClass().getName());
                }
            }
        }

    }
}
