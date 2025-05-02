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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppApplication;
import org.gradle.language.cpp.internal.DefaultCppPlatform;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import javax.inject.Inject;

import static org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost;
import static org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine;

/**
 * <p>A plugin that produces a native application from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppApplication} extension to the project to allow configuration of the application.</p>
 *
 * @since 4.5
 */
public abstract class CppApplicationPlugin implements Plugin<Project> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final AttributesFactory attributesFactory;
    private final TargetMachineFactory targetMachineFactory;

    @Inject
    public CppApplicationPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, AttributesFactory attributesFactory, TargetMachineFactory targetMachineFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.attributesFactory = attributesFactory;
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final ObjectFactory objectFactory = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        // Add the application and extension
        final DefaultCppApplication application = componentFactory.newInstance(CppApplication.class, DefaultCppApplication.class, "main");
        project.getExtensions().add(CppApplication.class, "application", application);
        project.getComponents().add(application);

        // Configure the component
        application.getBaseName().convention(project.getName());
        application.getTargetMachines().convention(useHostAsDefaultTargetMachine(targetMachineFactory));
        application.getDevelopmentBinary().convention(project.provider(() -> {
            // Use the debug variant as the development binary
            // Prefer the host architecture, if present, else use the first architecture specified
            return application.getBinaries().get().stream()
                    .filter(CppExecutable.class::isInstance)
                    .map(CppExecutable.class::cast)
                    .filter(binary -> !binary.isOptimized() && Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()).equals(DefaultNativePlatform.host().getArchitecture()))
                    .findFirst()
                    .orElseGet(() -> application.getBinaries().get().stream()
                            .filter(CppExecutable.class::isInstance)
                            .map(CppExecutable.class::cast)
                            .filter(binary -> !binary.isOptimized())
                            .findFirst()
                            .orElse(null));
        }));

        application.getBinaries().whenElementKnown(binary -> {
            application.getMainPublication().addVariant(binary);
        });

        project.afterEvaluate(p -> {
            // TODO: make build type configurable for components
            Dimensions.applicationVariants(application.getBaseName(), application.getTargetMachines(), objectFactory, attributesFactory,
                    providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                    variantIdentity -> {
                        if (tryToBuildOnHost(variantIdentity)) {
                            ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class, new DefaultCppPlatform(variantIdentity.getTargetMachine()));
                            application.addExecutable(variantIdentity, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                        } else {
                            // Known, but not buildable
                            application.getMainPublication().addVariant(variantIdentity);
                        }
                    });

            // Configure the binaries
            application.getBinaries().realizeNow();
        });
    }
}
