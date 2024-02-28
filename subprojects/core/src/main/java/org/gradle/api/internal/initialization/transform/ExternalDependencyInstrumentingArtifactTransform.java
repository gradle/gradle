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
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_MISSING_HASH;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.METADATA_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.createNewFile;
import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_DIR_NAME;
import static org.gradle.internal.classpath.TransformedClassPath.ORIGINAL_FILE_PLACEHOLDER_SUFFIX;

/**
 * Artifact transform that instruments external plugins with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class ExternalDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform {

    @Override
    public void transform(TransformOutputs outputs) {
        File inputDir = getInput().get().getAsFile();
        if (inputDir.getName().equals(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME)) {
            return;
        }
        if (maybeOutputOriginalFile(inputDir, outputs)) {
            return;
        }


        InstrumentationArtifactMetadata metadata = readArtifactMetadata(inputDir);
        if (metadata.getArtifactHash().equals(FILE_MISSING_HASH)) {
            execute(null, outputs, __ -> {});
        } else {
            String hash = metadata.getArtifactHash();
            long contextId = getParameters().getContextId().get();
            File originalArtifact = getParameters().getBuildService().get().getOriginalFile(contextId, hash);
            execute(originalArtifact, outputs, __ -> writeOriginalFilePlaceholder(hash, outputs));
        }
    }

    private boolean maybeOutputOriginalFile(File input, TransformOutputs outputs) {
        if (!new File(input, DEPENDENCIES_SUPER_TYPES_FILE_NAME).exists()) {
            handleOriginalJar(input, outputs);
            return true;
        }
        return false;
    }

    private static void writeOriginalFilePlaceholder(String hash, TransformOutputs outputs) {
        createNewFile(outputs.file(ORIGINAL_DIR_NAME + "/" + hash + ORIGINAL_FILE_PLACEHOLDER_SUFFIX));
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
