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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.instrumentation.reporting.MethodInterceptionReportCollector;
import org.gradle.internal.instrumentation.reporting.listener.BytecodeUpgradeReportMethodInterceptionListener;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.Optional;

import static org.gradle.api.internal.initialization.transform.ProjectDependencyInstrumentingArtifactTransform.Parameters;

/**
 * Artifact transform that instruments project based artifacts with Gradle instrumentation.
 */
@DisableCachingByDefault(because = "Instrumented jars are too big to cache.")
public abstract class ProjectDependencyInstrumentingArtifactTransform extends BaseInstrumentingArtifactTransform<Parameters> {

    public interface Parameters extends BaseInstrumentingArtifactTransform.Parameters {
        @Input
        Property<Boolean> getIsUpgradeReport();
    }

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
    protected InstrumentingClassTransformProvider instrumentingClassTransformProvider(TransformOutputs outputs) {
        Optional<BytecodeUpgradeReportMethodInterceptionListener> interceptionListener = getParameters().getIsUpgradeReport().getOrElse(false)
            ? Optional.of(new BytecodeUpgradeReportMethodInterceptionListener(outputs.file(MethodInterceptionReportCollector.INTERCEPTED_METHODS_REPORT_FILE)))
            : Optional.empty();

        return new InstrumentingClassTransformProvider() {
            @Override
            public InstrumentingClassTransform getClassTransform() {
                return interceptionListener
                    // TODO: Using gradleCoreTypeRegistry means we won't detect calls for user types that extend from Gradle types, fix that
                    .map(listener -> new InstrumentingClassTransform(BytecodeInterceptorFilter.INSTRUMENTATION_AND_BYTECODE_REPORT, getGradleCoreTypeRegistry(), listener))
                    .orElseGet(() -> new InstrumentingClassTransform(BytecodeInterceptorFilter.INSTRUMENTATION_ONLY, InstrumentationTypeRegistry.EMPTY));
            }

            @Override
            public void close() {
                interceptionListener.ifPresent(BytecodeUpgradeReportMethodInterceptionListener::close);
            }
        };
    }

    private InstrumentationTypeRegistry getGradleCoreTypeRegistry() {
        return internalServices.get().getGradleCoreInstrumentationTypeRegistry();
    }
}
