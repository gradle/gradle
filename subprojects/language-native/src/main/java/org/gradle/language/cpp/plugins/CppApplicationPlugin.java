/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppApplication;
import org.gradle.language.cpp.internal.NativeVariant;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;

import javax.inject.Inject;

/**
 * <p>A plugin that produces a native application from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppApplication} extension to the project to allow configuration of the application.</p>
 *
 * @since 4.5
 */
@Incubating
public class CppApplicationPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    @Inject
    public CppApplicationPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final ObjectFactory objectFactory = project.getObjects();

        // Add the application and extension
        final DefaultCppApplication application = componentFactory.newInstance(CppApplication.class, DefaultCppApplication.class, "main");
        project.getExtensions().add(CppApplication.class, "application", application);
        project.getComponents().add(application);

        // Configure the component
        application.getBaseName().set(project.getName());

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);

                CppExecutable debugExecutable = application.addExecutable("debug", true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                CppExecutable releaseExecutable = application.addExecutable("release", true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                // Use the debug variant as the development binary
                application.getDevelopmentBinary().set(debugExecutable);

                // Add publications

                final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                Configuration debugRuntimeElements = debugExecutable.getRuntimeElements().get();
                Configuration releaseRuntimeElements = releaseExecutable.getRuntimeElements().get();

                NativeVariant debugVariant = new NativeVariant("debug", runtimeUsage, debugRuntimeElements.getAllArtifacts(), debugRuntimeElements);
                application.getMainPublication().addVariant(debugVariant);
                NativeVariant releaseVariant = new NativeVariant("release", runtimeUsage, releaseRuntimeElements.getAllArtifacts(), releaseRuntimeElements);
                application.getMainPublication().addVariant(releaseVariant);

                application.getBinaries().realizeNow();
            }
        });
    }
}
