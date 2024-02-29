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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationAnalysisSerializer;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.PropertiesBackedInstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.METADATA_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.isAnalysisMetadataDir;

/**
 * Artifact transform that instruments external artifacts with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class ExternalDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform {

    @Override
    public void transform(TransformOutputs outputs) {
        // We simulate fan-in behaviour:
        // We expect that a transform before this one outputs two artifacts: 1. analysis metadata and 2. the original file.
        // So if the input is analysis metadata we use it and create instrumented artifact, otherwise it's original artifact and we output that.
        File input = getInput().get().getAsFile();
        if (isAnalysisMetadataDir(input)) {
            doOutputTransformedFile(input, outputs);
        } else if (getParameters().getAgentSupported().get()) {
            doOutputOriginalArtifact(input, outputs);
        }
    }

    private void doOutputTransformedFile(File input, TransformOutputs outputs) {
        InstrumentationArtifactMetadata metadata = readArtifactMetadata(input);
        long contextId = getParameters().getContextId().get();
        File originalArtifact = getParameters().getBuildService().get().getOriginalFile(contextId, metadata);
        doTransform(originalArtifact, outputs);
    }

    private InstrumentationArtifactMetadata readArtifactMetadata(File inputDir) {
        File metadata = new File(inputDir, METADATA_FILE_NAME);
        InstrumentationAnalysisSerializer serializer = new InstrumentationAnalysisSerializer(internalServices.get().getStringInterner());
        return serializer.readMetadata(metadata);
    }

    @Override
    protected InterceptorTypeRegistryAndFilter provideInterceptorTypeRegistryAndFilter() {
        return new InterceptorTypeRegistryAndFilter() {
            @Override
            public InstrumentationTypeRegistry getRegistry() {
                return PropertiesBackedInstrumentationTypeRegistry.of(() -> {
                    File dependenciesSuperTypes = new File(getInput().get().getAsFile(), DEPENDENCIES_SUPER_TYPES_FILE_NAME);
                    InstrumentationAnalysisSerializer serializer = new InstrumentationAnalysisSerializer(internalServices.get().getStringInterner());
                    return serializer.readTypesMap(dependenciesSuperTypes);
                });
            }

            @Override
            public BytecodeInterceptorFilter getFilter() {
                return BytecodeInterceptorFilter.ALL;
            }
        };
    }
}
