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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.util.Set;

public class CompilerApiData {

    private final boolean isAvailable;
    private final ConstantToDependentsMapping constantToDependentsMapping;

    private CompilerApiData(boolean isAvailable, ConstantToDependentsMapping constantToDependentsMapping) {
        this.isAvailable = isAvailable;
        this.constantToDependentsMapping = constantToDependentsMapping;
    }

    public Set<String> accessibleConstantDependentsForClass(String constantOrigin) {
        return constantToDependentsMapping.findAccessibleConstantDependentsFor(constantOrigin);
    }

    public Set<String> privateConstantDependentsForClass(String constantOrigin) {
        return constantToDependentsMapping.findPrivateConstantDependentsFor(constantOrigin);
    }

    public ConstantToDependentsMapping getConstantToClassMapping() {
        return constantToDependentsMapping;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public static CompilerApiData unavailableOf() {
        return new CompilerApiData(false, ConstantToDependentsMapping.empty());
    }

    public static CompilerApiData availableOf(ConstantToDependentsMapping constantToDependentsMapping) {
        return new CompilerApiData(true, constantToDependentsMapping);
    }

    public static final class Serializer extends AbstractSerializer<CompilerApiData> {
        private final ConstantToDependentsMapping.Serializer constantsToClassSerializer;

        public Serializer(StringInterner interner) {
            constantsToClassSerializer = new ConstantToDependentsMapping.Serializer(interner);
        }

        @Override
        public CompilerApiData read(Decoder decoder) throws Exception {
            boolean isAvailable = decoder.readBoolean();
            if (isAvailable) {
                ConstantToDependentsMapping mapping = constantsToClassSerializer.read(decoder);
                return CompilerApiData.availableOf(mapping);
            } else {
                return CompilerApiData.unavailableOf();
            }
        }

        @Override
        public void write(Encoder encoder, CompilerApiData value) throws Exception {
            encoder.writeBoolean(value.isAvailable());
            if (value.isAvailable()) {
                constantsToClassSerializer.write(encoder, value.getConstantToClassMapping());
            }
        }
    }

}
