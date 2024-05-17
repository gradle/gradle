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
import org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.InstrumentationInputType;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.PropertiesBackedInstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.getInputType;

/**
 * Artifact transform that instruments external artifacts with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class ExternalDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform {

    @Override
    public void transform(TransformOutputs outputs) {
        // We simulate fan-in behaviour:
        // We expect that a transform before this one outputs three artifacts: 1. analysis metadata, 2. the original file and 3. instrumentation marker file.
        // So if the input is analysis metadata we use it and create instrumented artifact, otherwise it's original artifact and we output that.
        File input = getInput().get().getAsFile();
        InstrumentationInputType inputType = getInputType(input);
        switch (inputType) {
            case DEPENDENCY_ANALYSIS_DATA:
                doOutputTransformedFile(input, outputs);
                return;
            case ORIGINAL_ARTIFACT:
                if (getParameters().getAgentSupported().get()) {
                    doOutputOriginalArtifact(input, outputs);
                }
                return;
            case INSTRUMENTATION_MARKER:
                // We don't need to do anything with the marker file
                return;
            case TYPE_HIERARCHY_ANALYSIS_DATA:
                // Type hierarchy analysis should never be an input to this transform
            default:
                throw new IllegalStateException("Unexpected input type: " + inputType);
        }
    }

    private void doOutputTransformedFile(File input, TransformOutputs outputs) {
        InstrumentationArtifactMetadata metadata = readArtifactMetadata(input);
        long contextId = getParameters().getContextId().get();
        File originalArtifact = getParameters().getBuildService().get().getOriginalFile(contextId, metadata);
        doTransform(originalArtifact, outputs);
    }

    private InstrumentationArtifactMetadata readArtifactMetadata(File input) {
        InstrumentationAnalysisSerializer serializer = getParameters().getBuildService().get().getCachedInstrumentationAnalysisSerializer();
        return serializer.readMetadataOnly(input);
    }

    @Override
    protected InterceptorTypeRegistryAndFilter provideInterceptorTypeRegistryAndFilter() {
        return new InterceptorTypeRegistryAndFilter() {
            @Override
            public InstrumentationTypeRegistry getRegistry() {
                return PropertiesBackedInstrumentationTypeRegistry.of(() -> {
                    File analysisFile = getInput().get().getAsFile();
                    InstrumentationAnalysisSerializer serializer = getParameters().getBuildService().get().getCachedInstrumentationAnalysisSerializer();
                    return serializer.readDependencyAnalysis(analysisFile).getDependencies();
                });
            }

            @Override
            public BytecodeInterceptorFilter getFilter() {
                return BytecodeInterceptorFilter.ALL;
            }
        };
    }
}
