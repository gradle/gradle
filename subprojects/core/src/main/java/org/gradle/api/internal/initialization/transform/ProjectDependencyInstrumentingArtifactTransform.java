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

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * Artifact transform that instruments project based artifacts with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache.")
public abstract class ProjectDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform {

    @Override
    @Classpath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    public void transform(TransformOutputs outputs) {
        File input = getInput().get().getAsFile();
        doTransform(input, outputs);
        if (getParameters().getAgentSupported().get()) {
            doOutputOriginalArtifact(input, outputs);
        }
    }

    @Override
    protected InterceptorTypeRegistryAndFilter provideInterceptorTypeRegistryAndFilter() {
        return new InterceptorTypeRegistryAndFilter() {
            @Override
            public InstrumentationTypeRegistry getRegistry() {
                return InstrumentationTypeRegistry.EMPTY;
            }

            @Override
            public BytecodeInterceptorFilter getFilter() {
                return BytecodeInterceptorFilter.INSTRUMENTATION_ONLY;
            }
        };
    }
}
