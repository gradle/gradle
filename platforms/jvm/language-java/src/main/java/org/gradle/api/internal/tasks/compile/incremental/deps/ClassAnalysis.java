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

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Set;

/**
 * An immutable set of details extracted from a class file.
 */
public class ClassAnalysis {
    private final String className;
    private final Set<String> privateClassDependencies;
    private final Set<String> accessibleClassDependencies;
    private final String dependencyToAllReason;
    private final IntSet constants;

    public ClassAnalysis(String className, Set<String> privateClassDependencies, Set<String> accessibleClassDependencies, String dependencyToAllReason, IntSet constants) {
        this.className = className;
        this.privateClassDependencies = ImmutableSet.copyOf(privateClassDependencies);
        this.accessibleClassDependencies = ImmutableSet.copyOf(accessibleClassDependencies);
        this.dependencyToAllReason = dependencyToAllReason;
        this.constants = constants.isEmpty() ? IntSets.EMPTY_SET : constants;
    }

    public String getClassName() {
        return className;
    }

    public Set<String> getPrivateClassDependencies() {
        return privateClassDependencies;
    }

    public Set<String> getAccessibleClassDependencies() {
        return accessibleClassDependencies;
    }

    public IntSet getConstants() {
        return constants;
    }

    public String getDependencyToAllReason() {
        return dependencyToAllReason;
    }

    public static class Serializer extends AbstractSerializer<ClassAnalysis> {

        private final StringInterner interner;
        private final SetSerializer<String> stringSetSerializer;

        public Serializer(StringInterner interner) {
            stringSetSerializer = new SetSerializer<>(new InterningStringSerializer(interner), false);
            this.interner = interner;
        }

        @Override
        public ClassAnalysis read(Decoder decoder) throws Exception {
            String className = interner.intern(decoder.readString());
            String dependencyToAllReason = decoder.readNullableString();
            Set<String> privateClasses = stringSetSerializer.read(decoder);
            Set<String> accessibleClasses = stringSetSerializer.read(decoder);
            IntSet constants = IntSetSerializer.INSTANCE.read(decoder);
            return new ClassAnalysis(className, privateClasses, accessibleClasses, dependencyToAllReason, constants);
        }

        @Override
        public void write(Encoder encoder, ClassAnalysis value) throws Exception {
            encoder.writeString(value.getClassName());
            encoder.writeNullableString(value.getDependencyToAllReason());
            stringSetSerializer.write(encoder, value.getPrivateClassDependencies());
            stringSetSerializer.write(encoder, value.getAccessibleClassDependencies());
            IntSetSerializer.INSTANCE.write(encoder, value.getConstants());
        }

    }
}
