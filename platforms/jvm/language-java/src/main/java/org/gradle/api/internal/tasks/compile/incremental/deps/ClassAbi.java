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
import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The ABI of a class file.
 */
@NullMarked
public final class ClassAbi {

    private final int access;
    private final @Nullable String signature;
    private final @Nullable String superName;
    private final List<String> interfaces;
    private final Map<String, FieldAbi> fieldAbis;
    private final Map<String, MethodAbi> methodAbis;

    public ClassAbi(int access, @Nullable String signature, @Nullable String superName, List<String> interfaces, Map<String, FieldAbi> fieldAbis, Map<String, MethodAbi> methodAbis) {
        this.access = access;
        this.signature = signature;
        this.superName = superName;
        this.interfaces = ImmutableList.copyOf(interfaces);
        this.fieldAbis = ImmutableMap.copyOf(fieldAbis);
        this.methodAbis = ImmutableMap.copyOf(methodAbis);
    }

    public int getAccess() {
        return access;
    }

    public @Nullable String getSignature() {
        return signature;
    }

    public @Nullable String getSuperName() {
        return superName;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public Map<String, FieldAbi> getFieldAbis() {
        return fieldAbis;
    }

    public Map<String, MethodAbi> getMethodAbis() {
        return methodAbis;
    }

    public boolean isCompatible(ClassAbi currentClassAbi) {
        if (access == currentClassAbi.access
            && Objects.equals(signature, currentClassAbi.signature)
            && Objects.equals(superName, currentClassAbi.superName)
            && Objects.equals(interfaces, currentClassAbi.interfaces)
        ) {
            for (Map.Entry<String, FieldAbi> entry : fieldAbis.entrySet()) {
                FieldAbi fieldAbi = currentClassAbi.fieldAbis.get(entry.getKey());
                if (fieldAbi == null || !entry.getValue().isCompatible(fieldAbi)) {
                    return false;
                }
            }
            for (Map.Entry<String, MethodAbi> entry : methodAbis.entrySet()) {
                MethodAbi methodAbi = currentClassAbi.methodAbis.get(entry.getKey());
                if (methodAbi == null || !entry.getValue().isCompatible(methodAbi)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @NullMarked
    public static class Serializer extends AbstractSerializer<ClassAbi> {

        private final StringInterner interner;
        private final ListSerializer<String> listSerializer;
        private final MapSerializer<String, FieldAbi> fieldAbisSerializer;
        private final MapSerializer<String, MethodAbi> methodAbisSerializer;

        public Serializer(StringInterner interner) {
            listSerializer = new ListSerializer<>(new InterningStringSerializer(interner));
            fieldAbisSerializer = new MapSerializer<>(new InterningStringSerializer(interner), new FieldAbi.Serializer(interner));
            methodAbisSerializer = new MapSerializer<>(new InterningStringSerializer(interner), new MethodAbi.Serializer(interner));
            this.interner = interner;
        }

        @Override
        public ClassAbi read(Decoder decoder) throws Exception {
            int access = decoder.readInt();
            String signature = interner.intern(decoder.readNullableString());
            String superName = interner.intern(decoder.readNullableString());
            List<String> interfaces = listSerializer.read(decoder);
            Map<String, FieldAbi> fieldAbis = fieldAbisSerializer.read(decoder);
            Map<String, MethodAbi> methodAbis = methodAbisSerializer.read(decoder);
            return new ClassAbi(access, signature, superName, interfaces, fieldAbis, methodAbis);
        }

        @Override
        public void write(Encoder encoder, ClassAbi value) throws Exception {
            encoder.writeInt(value.getAccess());
            encoder.writeNullableString(value.getSignature());
            encoder.writeNullableString(value.getSuperName());
            listSerializer.write(encoder, value.getInterfaces());
            fieldAbisSerializer.write(encoder, value.getFieldAbis());
            methodAbisSerializer.write(encoder, value.getMethodAbis());
        }

    }
}
