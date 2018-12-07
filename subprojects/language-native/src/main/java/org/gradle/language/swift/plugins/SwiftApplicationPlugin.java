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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.BuildType;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftExecutable;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftApplication;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;
import static org.gradle.language.nativeplatform.internal.Dimensions.createDimensionSuffix;

/**
 * <p>A plugin that produces an executable from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the executable. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftApplication} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.5
 */
@Incubating
public class SwiftApplicationPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final ImmutableAttributesFactory attributesFactory;
    private final TargetMachineFactory targetMachineFactory;

    /**
     * Injects a {@link FileOperations} instance.
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
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();

        // Add the application and extension
        final DefaultSwiftApplication application = componentFactory.newInstance(SwiftApplication.class, DefaultSwiftApplication.class, "main");
        project.getExtensions().add(SwiftApplication.class, "application", application);
        project.getComponents().add(application);

        // Setup component
        application.getModule().set(GUtil.toCamelCase(project.getName()));

        application.getTargetMachines().convention(Dimensions.getDefaultTargetMachines(targetMachineFactory));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                application.getTargetMachines().finalizeValue();
                Set<TargetMachine> targetMachines = application.getTargetMachines().get();
                if (targetMachines.isEmpty()) {
                    throw new IllegalArgumentException("A target machine needs to be specified for the application.");
                }

                final ObjectFactory objectFactory = project.getObjects();
                Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                for (BuildType buildType : BuildType.DEFAULT_BUILD_TYPES) {
                    for (TargetMachine targetMachine : targetMachines) {
                        String operatingSystemSuffix = createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachines.stream().map(TargetMachine::getOperatingSystemFamily).collect(Collectors.toSet()));
                        String architectureSuffix = createDimensionSuffix(targetMachine.getArchitecture(), targetMachines.stream().map(TargetMachine::getArchitecture).collect(Collectors.toSet()));
                        String variantName = buildType.getName() + operatingSystemSuffix + architectureSuffix;

                        Provider<String> group = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getGroup().toString();
                            }
                        });

                        Provider<String> version = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getVersion().toString();
                            }
                        });

                        AttributeContainer runtimeAttributes = attributesFactory.mutable();
                        runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        runtimeAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                        runtimeAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                        runtimeAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                        runtimeAttributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());

                        NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, application.getModule(), group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine,
                            null,
                            new DefaultUsageContext(variantName + "-runtime", runtimeUsage, runtimeAttributes));

                        if (DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(targetMachine.getOperatingSystemFamily().getName())) {
                            ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class, targetMachine);

                            SwiftExecutable executable = application.addExecutable(variantIdentity, buildType == BuildType.DEBUG, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                            // Use the debug variant as the development binary
                            if (buildType == BuildType.DEBUG) {
                                application.getDevelopmentBinary().set(executable);
                            }
                        }
                    }
                }

                // Configure the binaries
                application.getBinaries().realizeNow();
            }
        });
    }
}
