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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.language.cpp.internal.DefaultUsageContext;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.BuildType;
import org.gradle.language.nativeplatform.internal.ComponentWithNames;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.language.swift.SwiftComponent;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftPlatform;
import org.gradle.language.swift.SwiftSharedLibrary;
import org.gradle.language.swift.SwiftStaticLibrary;
import org.gradle.language.swift.internal.DefaultSwiftLibrary;
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary;
import org.gradle.language.swift.internal.DefaultSwiftStaticLibrary;
import org.gradle.nativeplatform.Linkage;
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
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;
import static org.gradle.language.nativeplatform.internal.Dimensions.createDimensionSuffix;
import static org.gradle.language.nativeplatform.internal.Dimensions.getDefaultTargetMachines;

/**
 * <p>A plugin that produces a shared library from Swift source.</p>
 *
 * <p>Adds compile, link and install tasks to build the shared library. Defaults to looking for source files in `src/main/swift`.</p>
 *
 * <p>Adds a {@link SwiftComponent} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.2
 */
@Incubating
public class SwiftLibraryPlugin implements Plugin<Project> {
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

        final ConfigurationContainer configurations = project.getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();

        final DefaultSwiftLibrary library = componentFactory.newInstance(SwiftLibrary.class, DefaultSwiftLibrary.class, "main");
        project.getExtensions().add(SwiftLibrary.class, "library", library);
        project.getComponents().add(library);

        // Setup component
        final Property<String> module = library.getModule();
        module.set(GUtil.toCamelCase(project.getName()));

        library.getTargetMachines().convention(getDefaultTargetMachines(targetMachineFactory));

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                library.getTargetMachines().finalizeValue();
                Set<TargetMachine> targetMachines = library.getTargetMachines().get();
                if (targetMachines.isEmpty()) {
                    throw new IllegalArgumentException("A target machine needs to be specified for the library.");
                }

                library.getLinkage().finalizeValue();
                Set<Linkage> linkages = library.getLinkage().get();
                if (linkages.isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);

                for (BuildType buildType : BuildType.DEFAULT_BUILD_TYPES) {
                    for (TargetMachine targetMachine : targetMachines) {
                        for (Linkage linkage : linkages) {

                            String operatingSystemSuffix = createDimensionSuffix(targetMachine.getOperatingSystemFamily(), targetMachines.stream().map(TargetMachine::getOperatingSystemFamily).collect(Collectors.toSet()));
                            String architectureSuffix = createDimensionSuffix(targetMachine.getArchitecture(), targetMachines.stream().map(TargetMachine::getArchitecture).collect(Collectors.toSet()));
                            String linkageSuffix = createDimensionSuffix(linkage, linkages);
                            String variantName = buildType.getName() + linkageSuffix + operatingSystemSuffix + architectureSuffix;

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
                            runtimeAttributes.attribute(LINKAGE_ATTRIBUTE, linkage);
                            runtimeAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                            runtimeAttributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());

                            AttributeContainer linkAttributes = attributesFactory.mutable();
                            linkAttributes.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                            linkAttributes.attribute(DEBUGGABLE_ATTRIBUTE, buildType.isDebuggable());
                            linkAttributes.attribute(OPTIMIZED_ATTRIBUTE, buildType.isOptimized());
                            linkAttributes.attribute(LINKAGE_ATTRIBUTE, linkage);
                            linkAttributes.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, targetMachine.getOperatingSystemFamily());
                            linkAttributes.attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, targetMachine.getArchitecture());

                            NativeVariantIdentity variantIdentity = new NativeVariantIdentity(variantName, library.getModule(), group, version, buildType.isDebuggable(), buildType.isOptimized(), targetMachine,
                                new DefaultUsageContext(variantName + "Link", linkUsage, linkAttributes),
                                new DefaultUsageContext(variantName + "Runtime", runtimeUsage, runtimeAttributes));
                            // TODO: publish Swift libraries
                            // library.getMainPublication().addVariant(variantIdentity);

                            if (DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(targetMachine.getOperatingSystemFamily().getName())) {
                                ToolChainSelector.Result<SwiftPlatform> result = toolChainSelector.select(SwiftPlatform.class, targetMachine);

                                if (linkage == Linkage.SHARED) {
                                    SwiftSharedLibrary sharedLibrary = library.addSharedLibrary(variantName, buildType == BuildType.DEBUG, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), variantIdentity);

                                    // Use the debug shared library as the development binary
                                    if (buildType == BuildType.DEBUG) {
                                        library.getDevelopmentBinary().set(sharedLibrary);
                                    }

                                } else {
                                    SwiftStaticLibrary staticLibrary = library.addStaticLibrary(variantName, buildType == BuildType.DEBUG, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), variantIdentity);

                                    if (!linkages.contains(Linkage.SHARED) && buildType == BuildType.DEBUG) {
                                        // Use the debug static library as the development binary
                                        library.getDevelopmentBinary().set(staticLibrary);
                                    }
                                }
                            }
                        }
                    }
                }

                library.getBinaries().whenElementKnown(SwiftSharedLibrary.class, new Action<SwiftSharedLibrary>() {
                    @Override
                    public void execute(SwiftSharedLibrary sharedLibrary) {
                        Names names = ((ComponentWithNames) sharedLibrary).getNames();
                        Configuration apiElements = configurations.create(names.withSuffix("SwiftApiElements"));
                        // TODO This should actually extend from the api dependencies, but since Swift currently
                        // requires all dependencies to be treated like api dependencies (with transitivity) we just
                        // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                        apiElements.extendsFrom(((DefaultSwiftSharedLibrary)sharedLibrary).getImplementationDependencies());
                        apiElements.setCanBeResolved(false);
                        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        apiElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.SHARED);
                        apiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, sharedLibrary.isDebuggable());
                        apiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, sharedLibrary.isOptimized());
                        apiElements.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, sharedLibrary.getTargetPlatform().getOperatingSystemFamily());
                        apiElements.getOutgoing().artifact(sharedLibrary.getModuleFile());
                    }
                });

                library.getBinaries().whenElementKnown(SwiftStaticLibrary.class, new Action<SwiftStaticLibrary>() {
                    @Override
                    public void execute(SwiftStaticLibrary staticLibrary) {
                        Names names = ((ComponentWithNames) staticLibrary).getNames();
                        Configuration apiElements = configurations.create(names.withSuffix("SwiftApiElements"));
                        // TODO This should actually extend from the api dependencies, but since Swift currently
                        // requires all dependencies to be treated like api dependencies (with transitivity) we just
                        // use the implementation dependencies here.  See https://bugs.swift.org/browse/SR-1393.
                        apiElements.extendsFrom(((DefaultSwiftStaticLibrary)staticLibrary).getImplementationDependencies());
                        apiElements.setCanBeResolved(false);
                        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage.class, Usage.SWIFT_API));
                        apiElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.STATIC);
                        apiElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, staticLibrary.isDebuggable());
                        apiElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, staticLibrary.isOptimized());
                        apiElements.getAttributes().attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, staticLibrary.getTargetPlatform().getOperatingSystemFamily());
                        apiElements.getOutgoing().artifact(staticLibrary.getModuleFile());
                    }
                });

                library.getBinaries().realizeNow();
            }
        });
    }
}
