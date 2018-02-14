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

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.ComponentWithCoordinates;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppApplication;
import org.gradle.language.cpp.internal.DefaultCppExecutable;
import org.gradle.language.cpp.internal.NativeVariant;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.platform.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.*;

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
                application.getOperatingSystems().lockNow();
                if (application.getOperatingSystems().get().isEmpty()) {
                    throw new IllegalArgumentException("An operating system needs to be specified for the application.");
                }

                for (OperatingSystem operatingSystem : application.getOperatingSystems().get()) {
                    // TODO: create variants
                    //  coordinates
                    //  attributes
                    //  usage
                    //  => name is attributes join together (-runtime, -link, -??)
                }

                boolean isHostCompatible = CollectionUtils.any(application.getOperatingSystems().get(), new Spec<OperatingSystem>() {
                    @Override
                    public boolean isSatisfiedBy(OperatingSystem element) {
                        return DefaultNativePlatform.getCurrentOperatingSystem().equals(element);
                    }
                });

                if (isHostCompatible) {
                    // TODO: create binary
                    //  toolchain
                    //  files
                    //  tasks
                    ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);

                    String os = application.getOperatingSystems().get().size() > 1 ? StringUtils.capitalize(DefaultNativePlatform.getCurrentOperatingSystem().getName().replaceAll("\\s+", "")) : "";

                    CppExecutable debugExecutable = application.addExecutable("debug" + os, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    application.addExecutable("release" + os, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    // Use the debug variant as the development binary
                    application.getDevelopmentBinary().set(debugExecutable);

                    application.getBinaries().realizeNow();
                }

                for (final OperatingSystem operatingSystem : application.getOperatingSystems().get()) {
                    if (!operatingSystem.equals(DefaultNativePlatform.getCurrentOperatingSystem())) {
                        String os = application.getOperatingSystems().get().size() > 1 ? StringUtils.capitalize(operatingSystem.getName().replaceAll("\\s+", "")) : "";

                        Map<Attribute<?>, Object> attributesDebug = new HashMap<Attribute<?>, Object>();
                        attributesDebug.put(DEBUGGABLE_ATTRIBUTE, true);
                        attributesDebug.put(OPTIMIZED_ATTRIBUTE, false);
                        attributesDebug.put(OPERATING_SYSTEM_ATTRIBUTE, ((OperatingSystemInternal) operatingSystem).getInternalOs().getFamilyName());
                        final String nameDebug = application.getName() + StringUtils.capitalize("debug" + os);
                        application.getMainPublication().addVariant(new SterlingNativeVariant(project.provider(new Callable<ModuleVersionIdentifier>() {
                                    @Override
                                    public ModuleVersionIdentifier call() throws Exception {
                                        return new DefaultModuleVersionIdentifier(project.getGroup().toString(), application.getBaseName().get() + "_" + GUtil.toWords(nameDebug, '_'), project.getVersion().toString());
                                    }
                                }), nameDebug, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME), Names.of(nameDebug), attributesDebug));

                        Map<Attribute<?>, Object> attributesRelease = new HashMap<Attribute<?>, Object>();
                        attributesRelease.put(DEBUGGABLE_ATTRIBUTE, true);
                        attributesRelease.put(OPTIMIZED_ATTRIBUTE, true);
                        attributesRelease.put(OPERATING_SYSTEM_ATTRIBUTE, ((OperatingSystemInternal) operatingSystem).getInternalOs().getFamilyName());
                        final String nameRelease = application.getName() + StringUtils.capitalize("release" + os);
                        application.getMainPublication().addVariant(new SterlingNativeVariant(project.provider(new Callable<ModuleVersionIdentifier>() {
                                    @Override
                                    public ModuleVersionIdentifier call() throws Exception {
                                        return new DefaultModuleVersionIdentifier(project.getGroup().toString(), application.getBaseName().get() + "_" + GUtil.toWords(nameRelease, '_'), project.getVersion().toString());
                                    }
                                }), nameRelease, objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME), Names.of(nameRelease), attributesRelease));
                    }
                }
            }
        });

        // Define publications
        // TODO - move this to a shared location

        final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
        application.getBinaries().whenElementKnown(DefaultCppExecutable.class, new Action<DefaultCppExecutable>() {
            @Override
            public void execute(DefaultCppExecutable executable) {
                Configuration runtimeElements = executable.getRuntimeElements().get();
                NativeVariant variant = new NativeVariant(executable.getNames(), runtimeUsage, runtimeElements.getAllArtifacts(), runtimeElements);
                application.getMainPublication().addVariant(variant);
            }
        });
    }

    public static class SterlingNativeVariant implements ComponentWithCoordinates, SoftwareComponentInternal {
        private final Provider<ModuleVersionIdentifier> provider;
        private final String name;
        private final Usage usage;
        private final Names names;
        private final Map<Attribute<?>, ?> attributes;

        SterlingNativeVariant(Provider<ModuleVersionIdentifier> provider, String name, Usage usage, Names names, Map<Attribute<?>, ?> attributes) {
            this.provider = provider;
            this.name = name;
            this.usage = usage;
            this.names = names;
            this.attributes = attributes;
        }

        @Override
        public Provider<ModuleVersionIdentifier> getCoordinates() {
            return provider;
        }

        // TODO: Use a narrower type that doesn't need other things
        @Override
        public Set<? extends UsageContext> getUsages() {
            return Sets.newHashSet(new UsageContext() {
                @Override
                public Usage getUsage() {
                    return usage;
                }

                @Override
                public Set<? extends PublishArtifact> getArtifacts() {
                    return null;
                }

                @Override
                public Set<? extends ModuleDependency> getDependencies() {
                    return null;
                }

                @Override
                public Set<? extends DependencyConstraint> getDependencyConstraints() {
                    return null;
                }

                @Override
                public String getName() {
                    return names.getLowerBaseName() + "-runtime";
                }

                @Override
                public AttributeContainer getAttributes() {
                    return new AttributeContainer() {
                        @Override
                        public Set<Attribute<?>> keySet() {
                            return attributes.keySet();
                        }

                        @Override
                        public <T> AttributeContainer attribute(Attribute<T> key, T value) {
                            return null;
                        }

                        @Nullable
                        @Override
                        public <T> T getAttribute(Attribute<T> key) {
                            return (T) attributes.get(key);
                        }

                        @Override
                        public boolean isEmpty() {
                            return attributes.isEmpty();
                        }

                        @Override
                        public boolean contains(Attribute<?> key) {
                            return attributes.containsKey(key);
                        }

                        @Override
                        public AttributeContainer getAttributes() {
                            return this;
                        }
                    };
                }
            });
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
