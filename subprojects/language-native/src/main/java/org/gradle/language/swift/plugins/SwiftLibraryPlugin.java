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
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.ComponentWithNames;
import org.gradle.language.nativeplatform.internal.Dimensions;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.internal.DefaultSwiftPlatform;
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary;
import org.gradle.language.swift.internal.DefaultSwiftStaticLibrary;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.util.internal.GUtil;

import javax.inject.Inject;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;
import static org.gradle.language.nativeplatform.internal.Dimensions.tryToBuildOnHost;
import static org.gradle.language.nativeplatform.internal.Dimensions.useHostAsDefaultTargetMachine;

/**
 * <p>A plugin that produces a shared library from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the shared library. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftComponent} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.2
 */
public abstract class SwiftLibraryPlugin implements Plugin<Project> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;
    private final ImmutableAttributesFactory attributesFactory;
    private final TargetMachineFactory targetMachineFactory;

    @Inject
    public SwiftLibraryPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ImmutableAttributesFactory attributesFactory, TargetMachineFactory targetMachineFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.attributesFactory = attributesFactory;
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(SwiftBasePlugin.class);

        final RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        final DefaultSwiftLibrary library = componentFactory.newInstance(SwiftLibrary.class, DefaultSwiftLibrary.class, "main");
        project.getExtensions().add(SwiftLibrary.class, "library", library);
        project.getComponents().add(library);

        // Setup component
        final Property<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));

        library.getTargetMachines().convention(useHostAsDefaultTargetMachine(targetMachineFactory));
        library.getDevelopmentBinary().convention(project.provider(new Callable<SwiftBinary>() {
            @Override
            public SwiftBinary call() throws Exception {
                return getDebugSharedHostStream().findFirst().orElseGet(
                        () -> getDebugStaticHostStream().findFirst().orElseGet(
                                () -> getDebugSharedStream().findFirst().orElseGet(
                                        () -> getDebugStaticStream().findFirst().orElse(null))));
            }

            private Stream<SwiftBinary> getDebugStream() {
                return library.getBinaries().get().stream().filter(binary -> !binary.isOptimized());
            }

            private Stream<SwiftBinary> getDebugSharedStream() {
                return getDebugStream().filter(SwiftSharedLibrary.class::isInstance);
            }

            private Stream<SwiftBinary> getDebugSharedHostStream() {
                return getDebugSharedStream().filter(binary -> Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()).equals(DefaultNativePlatform.host().getArchitecture()));
            }

            private Stream<SwiftBinary> getDebugStaticStream() {
                return getDebugStream().filter(SwiftStaticLibrary.class::isInstance);
            }

            private Stream<SwiftBinary> getDebugStaticHostStream() {
                return getDebugStaticStream().filter(binary -> Architectures.forInput(binary.getTargetMachine().getArchitecture().getName()).equals(DefaultNativePlatform.host().getArchitecture()));
            }
        }));

        project.afterEvaluate(p -> {
            // TODO: make build type configurable for components
            Dimensions.libraryVariants(library.getModule(), library.getLinkage(), library.getTargetMachines(), objectFactory, attributesFactory,
                    providers.provider(() -> project.getGroup().toString()), providers.provider(() -> project.getVersion().toString()),
                    variantIdentity -> {
                        if (tryToBuildOnHost(variantIdentity)) {
                            library.getSourceCompatibility().finalizeValue();
                            ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class, new DefaultSwiftPlatform(variantIdentity.getTargetMachine(), library.getSourceCompatibility().getOrNull()));

                            if (variantIdentity.getLinkage().equals(Linkage.SHARED)) {
                                library.addSharedLibrary(variantIdentity, variantIdentity.isDebuggable() && !variantIdentity.isOptimized(), result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                            } else {
                                library.addStaticLibrary(variantIdentity, variantIdentity.isDebuggable() && !variantIdentity.isOptimized(), result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                            }
                        }
                    });

            library.getBinaries().whenElementKnown(SwiftSharedLibrary.class, sharedLibrary -> {
                Names names = ((ComponentWithNames) sharedLibrary).getNames();
                configurations.migratingUnlocked(names.withSuffix("SwiftApiElements"), ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE, apiElements -> {
                    // TODO This should actually extend from the api dependencies, but since Swift currently
                    // requires all dependencies to be treated like api dependencies (with transitivity) we just
                    // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                    apiElements.extendsFrom(((DefaultSwiftSharedLibrary) sharedLibrary).getImplementationDependencies());
                    apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    apiElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.SHARED);
                    apiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, sharedLibrary.isDebuggable());
                    apiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, sharedLibrary.isOptimized());
                    apiElements.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, sharedLibrary.getTargetMachine().getOperatingSystemFamily());
                    apiElements.getOutgoing().artifact(sharedLibrary.getModuleFile());
                });
            });

            library.getBinaries().whenElementKnown(SwiftStaticLibrary.class, staticLibrary -> {
                Names names = ((ComponentWithNames) staticLibrary).getNames();
                configurations.migratingUnlocked(names.withSuffix("SwiftApiElements"), ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE, apiElements -> {
                    // TODO This should actually extend from the api dependencies, but since Swift currently
                    // requires all dependencies to be treated like api dependencies (with transitivity) we just
                    // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                    apiElements.extendsFrom(((DefaultSwiftStaticLibrary) staticLibrary).getImplementationDependencies());
                    apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                    apiElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.STATIC);
                    apiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, staticLibrary.isDebuggable());
                    apiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, staticLibrary.isOptimized());
                    apiElements.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, staticLibrary.getTargetMachine().getOperatingSystemFamily());
                    apiElements.getOutgoing().artifact(staticLibrary.getModuleFile());
                });
            });

            library.getBinaries().realizeNow();
        });
    }
}
