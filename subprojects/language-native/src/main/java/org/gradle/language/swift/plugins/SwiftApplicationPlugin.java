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

package org.gradle.language.swift.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftApplication;
import org.gradle.language.swift.internal.DefaultSwiftPlatform;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.util.internal.GUtil;

import javax.inject.Inject;

import static org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost;

/**
 * <p>A plugin that produces an executable from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftApplication} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.5
 */
public class SwiftApplicationPlugin implements Plugin<Project> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final ImmutableAttributesFactory attributesFactory;
    private final TargetMachineFactory targetMachineFactory;

    /**
     * SwiftApplicationPlugin.
     *
     * @since 4.2
     */
    @Inject
    public SwiftApplicationPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ImmutableAttributesFactory attributesFactory, TargetMachineFactory targetMachineFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.attributesFactory = attributesFactory;
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final ObjectFactory objectFactory = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        // Add the application and extension
        final DefaultSwiftApplication application = componentFactory.newInstance(SwiftApplication.class, DefaultSwiftApplication.class, "main");
        project.getExtensions().add(SwiftApplication.class, "application", application);
        project.getComponents().add(application);

        // Setup component
        application.getModule().convention(GUtil.toCamelCase(project.getName()));

        application.getTargetMachines().convention(Dimensions.useHostAsDefaultTargetMachine(targetMachineFactory));
        application.getDevelopmentBinary().convention(project.provider(() -> {
            return application.getBinaries().get().stream()
                    .filter(SwiftExecutable.class::isInstance)
                    .map(SwiftExecutable.class::cast)
                    .filter(binary -> !binary.isOptimized() && Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()).equals(DefaultNativePlatform.host().getArchitecture()))
                    .findFirst()
                    .orElse(application.getBinaries().get().stream()
                            .filter(SwiftExecutable.class::isInstance)
                            .map(SwiftExecutable.class::cast)
                            .filter(binary -> !binary.isOptimized())
                            .findFirst()
                            .orElse(null));
        }));

        project.afterEvaluate(p -> {
            // TODO: make build type configurable for components
            Dimensions.applicationVariants(application.getModule(), application.getTargetMachines(), objectFactory, attributesFactory,
                    providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                    variantIdentity -> {
                        if (tryToBuildOnHost(variantIdentity)) {
                            application.getSourceCompatibility().finalizeValue();
                            ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class, new DefaultSwiftPlatform(variantIdentity.getTargetMachine(), application.getSourceCompatibility().getOrNull()));
                            application.addExecutable(variantIdentity, variantIdentity.isDebuggable() && !variantIdentity.isOptimized(), result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                        }
                    });

            // Configure the binaries
            application.getBinaries().realizeNow();
        });
    }
}
