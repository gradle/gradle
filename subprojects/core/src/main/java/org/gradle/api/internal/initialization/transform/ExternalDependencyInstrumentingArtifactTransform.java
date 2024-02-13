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
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.gradle.api.internal.initialization.transform.CollectDirectClassSuperTypesTransform.FILE_HASH_PROPERTY_NAME;
import static org.gradle.api.internal.initialization.transform.MergeSuperTypesTransform.MERGED_SUPER_TYPES_SUFFIX;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.findFirstWithSuffix;

/**
 * Artifact transform that instruments external plugins with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache")
public abstract class ExternalDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform {

    @Override
    protected BytecodeInterceptorFilter provideInterceptorFilter() {
        return BytecodeInterceptorFilter.ALL;
    }

    @Override
    protected File inputArtifact() {
        try {
            File input = findFirstWithSuffix(getInput().get().getAsFile(), MERGED_SUPER_TYPES_SUFFIX);
            String hash = Files.asCharSource(input, StandardCharsets.UTF_8)
                .readFirstLine()
                .replace(FILE_HASH_PROPERTY_NAME + "=", "");
            return getParameters().getBuildService().get().getOriginalFile(hash);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
