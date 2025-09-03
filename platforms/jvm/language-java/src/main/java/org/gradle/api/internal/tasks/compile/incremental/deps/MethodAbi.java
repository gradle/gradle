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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The ABI of a class file method.
 */
@NullMarked
public final class MethodAbi {

    private final int access;
    private final String descriptor;
    private final @Nullable String signature;
    private final List<String> exceptions;

    public MethodAbi(int access, String descriptor, @Nullable String signature, List<String> exceptions) {
        this.access = access;
        this.descriptor = descriptor;
        this.signature = signature;
        this.exceptions = ImmutableList.copyOf(exceptions);
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

    public List<String> getExceptions() {
        return exceptions;
    }

    public boolean isCompatible(MethodAbi methodAbi) {
        return access == methodAbi.access
            && descriptor.equals(methodAbi.descriptor)
            && Objects.equals(signature, methodAbi.signature)
            && Objects.equals(exceptions, methodAbi.exceptions);
    }

    @NullMarked
    public static class Serializer extends AbstractSerializer<MethodAbi> {

        private final StringInterner interner;
        private final ListSerializer<String> stringListSerializer;

        public Serializer(StringInterner interner) {
            stringListSerializer = new ListSerializer<>(new InterningStringSerializer(interner));
            this.interner = interner;
        }

        @Override
        public MethodAbi read(Decoder decoder) throws Exception {
            int access = decoder.readInt();
            String descriptor = interner.intern(decoder.readString());
            String signature = interner.intern(decoder.readNullableString());
            List<String> exceptions = stringListSerializer.read(decoder);
            return new MethodAbi(access, descriptor, signature, exceptions);
        }

        @Override
        public void write(Encoder encoder, MethodAbi value) throws Exception {
            encoder.writeInt(value.getAccess());
            encoder.writeString(value.getDescriptor());
            encoder.writeNullableString(value.getSignature());
            stringListSerializer.write(encoder, value.getExceptions());
        }

    }
}
