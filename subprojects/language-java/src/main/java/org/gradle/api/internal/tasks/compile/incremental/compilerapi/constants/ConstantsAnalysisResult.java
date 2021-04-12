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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants;

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToClassMapping.ConstantToClassMappingBuilder;
import org.gradle.internal.compiler.java.CompilerConstantDependentsConsumer;

public class ConstantsAnalysisResult implements CompilerConstantDependentsConsumer {

    private final ConstantToClassMappingBuilder constantToClassMappingBuilder;

    public ConstantsAnalysisResult() {
        this.constantToClassMappingBuilder = new ConstantToClassMappingBuilder();
    }

    public ConstantToClassMapping getConstantsToClassMapping() {
        return constantToClassMappingBuilder.build();
    }

    @Override
    public void consumePublicDependent(String constantOrigin, String constantDependent) {
        constantToClassMappingBuilder.addPublicDependent(constantOrigin.hashCode(), constantDependent);
    }

    @Override
    public void consumePrivateDependent(String constantOrigin, String constantDependent) {
        constantToClassMappingBuilder.addPrivateDependent(constantOrigin.hashCode(), constantDependent);
    }
}
