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
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToClassMapping;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.util.Set;

public class CompilerApiData {

    private final boolean isAvailable;
    private final ConstantToClassMapping constantToClassMapping;

    public CompilerApiData() {
        this.isAvailable = false;
        this.constantToClassMapping = ConstantToClassMapping.empty();
    }

    public CompilerApiData(ConstantToClassMapping constantToClassMapping) {
        this.isAvailable = true;
        this.constantToClassMapping = constantToClassMapping;
    }

    public Set<String> constantDependentsForClassHash(int constantOriginHash) {
        return constantToClassMapping.constantDependentsForClassHash(constantOriginHash);
    }

    public ConstantToClassMapping getConstantToClassMapping() {
        return constantToClassMapping;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public static final class Serializer extends AbstractSerializer<CompilerApiData> {
        private final ConstantToClassMapping.Serializer constantsToClassSerializer;

        public Serializer(StringInterner interner) {
            constantsToClassSerializer = new ConstantToClassMapping.Serializer(interner);
        }

        @Override
        public CompilerApiData read(Decoder decoder) throws Exception {
            ConstantToClassMapping mapping = constantsToClassSerializer.read(decoder);
            return new CompilerApiData(mapping);
        }

        @Override
        public void write(Encoder encoder, CompilerApiData value) throws Exception {
            constantsToClassSerializer.write(encoder, value.getConstantToClassMapping());
        }
    }

}
