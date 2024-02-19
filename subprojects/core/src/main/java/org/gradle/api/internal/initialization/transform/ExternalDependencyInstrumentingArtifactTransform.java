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

import com.google.common.io.Files;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.PropertiesBackedInstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.DEPENDENCIES_SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.FILE_HASH_PROPERTY_NAME;
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
        File input = getInput().get().getAsFile();
        if (input.getName().equals(INSTRUMENTATION_CLASSPATH_MARKER_FILE_NAME)) {
            return;
        }

        File metadata = new File(input, METADATA_FILE_NAME);
        String hash = readOriginalHash(metadata);
        if (hash.equals(FILE_MISSING_HASH)) {
            execute(null, outputs, __ -> {});
        } else {
            File originalArtifact = checkNotNull(getParameters().getBuildService().get().getOriginalFile(hash));
            execute(originalArtifact, outputs, __ -> writeOriginalFilePlaceholder(hash, outputs));
        }
    }

    private static void writeOriginalFilePlaceholder(String hash, TransformOutputs outputs) {
        createNewFile(outputs.file(ORIGINAL_DIR_NAME + "/" + hash + ORIGINAL_FILE_PLACEHOLDER_SUFFIX));
    }

    private static String readOriginalHash(File metadata) {
        try {
            return Files.readLines(metadata, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith(FILE_HASH_PROPERTY_NAME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Metadata file does not contain hash"))
                .replace(FILE_HASH_PROPERTY_NAME + "=", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected InterceptorTypeRegistryAndFilter provideInterceptorTypeRegistryAndFilter() {
        return new InterceptorTypeRegistryAndFilter() {
            @Override
            public InstrumentationTypeRegistry getRegistry() {
                File properties = new File(getInput().get().getAsFile(), DEPENDENCIES_SUPER_TYPES_FILE_NAME);
                return PropertiesBackedInstrumentationTypeRegistry.of(properties);
            }

            @Override
            public BytecodeInterceptorFilter getFilter() {
                return BytecodeInterceptorFilter.ALL;
            }
        };
    }
}
