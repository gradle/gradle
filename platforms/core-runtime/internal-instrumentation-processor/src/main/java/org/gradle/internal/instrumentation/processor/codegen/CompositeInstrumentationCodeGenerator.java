/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen;

import com.squareup.javapoet.TypeSpec;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CanGenerateClasses;
import org.gradle.internal.instrumentation.processor.codegen.InstrumentationCodeGenerator.GenerationResult.CodeFailures;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompositeInstrumentationCodeGenerator implements InstrumentationCodeGenerator {

    private final Collection<InstrumentationCodeGenerator> generators;

    public CompositeInstrumentationCodeGenerator(Collection<InstrumentationCodeGenerator> generators) {
        this.generators = generators;
    }

    @Override
    public GenerationResult generateCodeForRequestedInterceptors(Collection<CallInterceptionRequest> interceptionRequests) {
        List<GenerationResult> results = generators.stream().map(generators -> generators.generateCodeForRequestedInterceptors(interceptionRequests)).collect(Collectors.toList());

        List<HasFailures> failures = results.stream().filter(it -> it instanceof HasFailures).map(it -> (HasFailures) it).collect(Collectors.toList());
        if (!failures.isEmpty()) {
            return new CodeFailures(failures.stream().flatMap(it -> it.getFailureDetails().stream()).collect(Collectors.toList()));
        }

        List<CanGenerateClasses> generatingResults = results.stream().map(it -> (CanGenerateClasses) it).collect(Collectors.toList());
        Map<String, CanGenerateClasses> generatorByClassName = new LinkedHashMap<>();
        generatingResults.forEach(result -> result.getClassNames().forEach(className -> {
            if (generatorByClassName.put(className, result) != null) {
                throw new IllegalStateException("multiple code generators for class name " + className);
            }
        }));

        return new CanGenerateClasses() {
            @Override
            public Collection<String> getClassNames() {
                return generatingResults.stream().flatMap(it -> it.getClassNames().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
            }

            @Override
            public void buildType(String className, TypeSpec.Builder builder) {
                generatorByClassName.get(className).buildType(className, builder);
            }

            @Override
            public List<CallInterceptionRequest> getCoveredRequests() {
                return generatingResults.stream().flatMap(it -> it.getCoveredRequests().stream()).distinct().collect(Collectors.toList());
            }
        };
    }
}
