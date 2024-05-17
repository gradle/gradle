/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi;

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentSetSerializer;
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class CompilerApiData {

    private final boolean isAvailable;
    private final boolean supportsConstantsMapping;
    private final Map<String, Set<String>> sourceToClassMapping;
    private final ConstantToDependentsMapping constantToDependentsMapping;

    private CompilerApiData(boolean isAvailable, boolean supportsConstantsMapping, Map<String, Set<String>> sourceToClassMapping, ConstantToDependentsMapping constantToDependentsMapping) {
        this.isAvailable = isAvailable;
        this.supportsConstantsMapping = supportsConstantsMapping;
        this.sourceToClassMapping = sourceToClassMapping;
        this.constantToDependentsMapping = constantToDependentsMapping;
    }

    public DependentsSet getConstantDependentsForClass(String constantOrigin) {
        return constantToDependentsMapping.getConstantDependentsForClass(constantOrigin);
    }

    public ConstantToDependentsMapping getConstantToClassMapping() {
        return constantToDependentsMapping;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public boolean isSupportsConstantsMapping() {
        return supportsConstantsMapping;
    }

    public static CompilerApiData unavailable() {
        return new CompilerApiData(false, false, Collections.emptyMap(), ConstantToDependentsMapping.empty());
    }

    public static CompilerApiData withoutConstantsMapping(Map<String, Set<String>> sourceToClassMapping) {
        return new CompilerApiData(true, false, sourceToClassMapping, ConstantToDependentsMapping.empty());
    }

    public static CompilerApiData withConstantsMapping(Map<String, Set<String>> sourceToClassMapping, ConstantToDependentsMapping constantToDependentsMapping) {
        return new CompilerApiData(true, true, sourceToClassMapping, constantToDependentsMapping);
    }

    public Map<String, Set<String>> getSourceToClassMapping() {
        return sourceToClassMapping;
    }

    public static final class Serializer extends AbstractSerializer<CompilerApiData> {

        private final Supplier<HierarchicalNameSerializer> classNameSerializerSupplier;
        private final DependentSetSerializer dependentSetSerializer;

        public Serializer(Supplier<HierarchicalNameSerializer> classNameSerializerSupplier) {
            this.classNameSerializerSupplier = classNameSerializerSupplier;
            this.dependentSetSerializer = new DependentSetSerializer(classNameSerializerSupplier);
        }

        @Override
        public CompilerApiData read(Decoder decoder) throws Exception {
            boolean isAvailable = decoder.readBoolean();
            if (!isAvailable) {
                return CompilerApiData.unavailable();
            }
            HierarchicalNameSerializer nameSerializer = classNameSerializerSupplier.get();
            MapSerializer<String, Set<String>> sourceToClassSerializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));

            Map<String, Set<String>> sourceToClassMapping = sourceToClassSerializer.read(decoder);
            if (!decoder.readBoolean()) {
                return CompilerApiData.withoutConstantsMapping(sourceToClassMapping);
            }

            MapSerializer<String, DependentsSet> constantDependentsSerializer = new MapSerializer<>(nameSerializer, dependentSetSerializer);
            Map<String, DependentsSet> constantDependents = constantDependentsSerializer.read(decoder);
            return CompilerApiData.withConstantsMapping(sourceToClassMapping, new ConstantToDependentsMapping(constantDependents));
        }

        @Override
        public void write(Encoder encoder, CompilerApiData value) throws Exception {
            encoder.writeBoolean(value.isAvailable());
            if (value.isAvailable()) {
                HierarchicalNameSerializer nameSerializer = classNameSerializerSupplier.get();
                MapSerializer<String, Set<String>> sourceToClassSerializer = new MapSerializer<>(nameSerializer, new SetSerializer<>(nameSerializer));

                sourceToClassSerializer.write(encoder, value.getSourceToClassMapping());
                boolean supportsConstantsMapping = value.isSupportsConstantsMapping();
                encoder.writeBoolean(supportsConstantsMapping);
                if (supportsConstantsMapping) {
                    MapSerializer<String, DependentsSet> constantDependentsSerializer = new MapSerializer<>(nameSerializer, dependentSetSerializer);
                    constantDependentsSerializer.write(encoder, value.getConstantToClassMapping().getConstantDependents());
                }
            }
        }
    }

}
