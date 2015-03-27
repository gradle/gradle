/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.plugins;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.*;
import org.gradle.nativeplatform.internal.configure.*;
import org.gradle.nativeplatform.internal.pch.DefaultPreCompiledHeaderTransformContainer;
import org.gradle.nativeplatform.internal.pch.PreCompiledHeaderTransformContainer;
import org.gradle.nativeplatform.internal.prebuilt.DefaultPrebuiltLibraries;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryInitializer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.tasks.PrefixHeaderFileGenerateTask;
import org.gradle.nativeplatform.toolchain.internal.DefaultNativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.PlatformResolvers;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeComponentModelPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;

    @Inject
    public NativeComponentModelPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);

        project.getExtensions().create("buildTypes", DefaultBuildTypeContainer.class, instantiator);
        project.getExtensions().create("flavors", DefaultFlavorContainer.class, instantiator);
        project.getExtensions().create("toolChains", DefaultNativeToolChainRegistry.class, instantiator);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @ComponentType
        void nativeExecutable(ComponentTypeBuilder<NativeExecutableSpec> builder) {
            builder.defaultImplementation(DefaultNativeExecutableSpec.class);
        }

        @ComponentType
        void nativeLibrary(ComponentTypeBuilder<NativeLibrarySpec> builder) {
            builder.defaultImplementation(DefaultNativeLibrarySpec.class);
        }

        @Model
        Repositories repositories(ServiceRegistry serviceRegistry, FlavorContainer flavors, PlatformContainer platforms, BuildTypeContainer buildTypes) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            Action<PrebuiltLibrary> initializer = new PrebuiltLibraryInitializer(instantiator, platforms.withType(NativePlatform.class), buildTypes, flavors);
            return new DefaultRepositories(instantiator, fileResolver, initializer);
        }

        @Model
        NativeToolChainRegistryInternal toolChains(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(NativeToolChainRegistryInternal.class);
        }

        @Model
        BuildTypeContainer buildTypes(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(BuildTypeContainer.class);
        }

        @Model
        FlavorContainer flavors(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(FlavorContainer.class);
        }

        @Model
        NamedDomainObjectSet<NativeComponentSpec> nativeComponents(ComponentSpecContainer components) {
            return components.withType(NativeComponentSpec.class);
        }

        @Model
        PreCompiledHeaderTransformContainer preCompiledHeaderTransformContainer(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(Instantiator.class).newInstance(DefaultPreCompiledHeaderTransformContainer.class);
        }

        @Mutate
        public void registerNativePlatformResolver(PlatformResolvers resolvers) {
            resolvers.register(new NativePlatformResolver());
        }

        @Defaults
        public void registerFactoryForCustomNativePlatforms(PlatformContainer platforms, ServiceRegistry serviceRegistry) {
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NamedDomainObjectFactory<NativePlatform> nativePlatformFactory = new NamedDomainObjectFactory<NativePlatform>() {
                public NativePlatform create(String name) {
                    return instantiator.newInstance(DefaultNativePlatform.class, name);
                }
            };

            platforms.registerFactory(NativePlatform.class, nativePlatformFactory);

            // TODO:DAZ This is only here for backward compatibility: platforms should be typed on creation, I think.
            platforms.registerFactory(Platform.class, nativePlatformFactory);
        }

        // TODO:DAZ Migrate to @BinaryType and @ComponentBinaries
        @Mutate
        public void createNativeBinaries(BinaryContainer binaries, NamedDomainObjectSet<NativeComponentSpec> nativeComponents,
                                         LanguageTransformContainer languageTransforms, NativeToolChainRegistryInternal toolChains,
                                         PlatformResolvers platforms, BuildTypeContainer buildTypes, FlavorContainer flavors,
                                         ServiceRegistry serviceRegistry, @Path("buildDir") File buildDir, ITaskFactory taskFactory) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            Action<NativeBinarySpec> configureBinaryAction = new NativeBinarySpecInitializer(buildDir);
            Action<NativeBinarySpec> setToolsAction = new ToolSettingNativeBinaryInitializer(languageTransforms);
            @SuppressWarnings("unchecked") Action<NativeBinarySpec> initAction = Actions.composite(configureBinaryAction, setToolsAction);
            NativeBinariesFactory factory = new DefaultNativeBinariesFactory(instantiator, initAction, resolver, taskFactory);
            BinaryNamingSchemeBuilder namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder();
            Action<NativeComponentSpec> createBinariesAction =
                    new NativeComponentSpecInitializer(factory, namingSchemeBuilder, toolChains, platforms, buildTypes, flavors);

            for (NativeComponentSpec component : nativeComponents) {
                createBinariesAction.execute(component);
                binaries.addAll(component.getBinaries());
            }
        }

        @Finalize
        public void createDefaultToolChain(NativeToolChainRegistryInternal toolChains) {
            if (toolChains.isEmpty()) {
                toolChains.addDefaultToolChains();
            }
        }

        @Finalize
        public void createDefaultBuildTypes(BuildTypeContainer buildTypes) {
            if (buildTypes.isEmpty()) {
                buildTypes.create("debug");
            }
        }

        @Finalize
        public void createDefaultFlavor(FlavorContainer flavors) {
            if (flavors.isEmpty()) {
                flavors.create(DefaultFlavor.DEFAULT);
            }
        }

        @Mutate
        void configureGeneratedSourceSets(CollectionBuilder<ComponentSpec> componentSpecs) {
            componentSpecs.afterEach(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    for (LanguageSourceSetInternal languageSourceSet : componentSpec.getSource().withType(LanguageSourceSetInternal.class)) {
                        Task generatorTask = languageSourceSet.getGeneratorTask();
                        if (generatorTask != null) {
                            languageSourceSet.builtBy(generatorTask);
                            maybeSetSourceDir(languageSourceSet.getSource(), generatorTask, "sourceDir");
                            if (languageSourceSet instanceof HeaderExportingSourceSet) {
                                maybeSetSourceDir(((HeaderExportingSourceSet) languageSourceSet).getExportedHeaders(), generatorTask, "headerDir");
                            }
                        }
                    }
                }
            });
        }

        @Mutate
        void configurePrefixHeaderFiles(CollectionBuilder<ComponentSpec> componentSpecs, final @Path("buildDir") File buildDir) {
            componentSpecs.afterEach(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    for (DependentSourceSet dependentSourceSet : componentSpec.getSource().withType(DependentSourceSet.class)) {
                        if (CollectionUtils.isNotEmpty(dependentSourceSet.getPreCompiledHeaders())) {
                            String prefixHeaderDirName = String.format("tmp/%s/prefixHeaders", dependentSourceSet.getName());
                            File prefixHeaderDir = new File(buildDir, prefixHeaderDirName);
                            final File prefixHeaderFile = new File(prefixHeaderDir, "prefix-headers.h");
                            dependentSourceSet.setPrefixHeaderFile(prefixHeaderFile);
                        }
                    }
                }
            });
        }

        @Mutate
        void configurePrefixHeaderGenerationTasks(final TaskContainer tasks, NamedDomainObjectSet<NativeComponentSpec> nativeComponents) {
            for (NativeComponentSpec nativeComponentSpec : nativeComponents) {
                nativeComponentSpec.getSource().withType(DependentSourceSet.class, new Action<DependentSourceSet>() {
                    @Override
                    public void execute(final DependentSourceSet dependentSourceSet) {
                        if (dependentSourceSet.getPrefixHeaderFile() !=  null) {
                            String taskName = String.format("generate%sPrefixHeaderFile", StringUtils.capitalize(dependentSourceSet.getName()));
                            tasks.create(taskName, PrefixHeaderFileGenerateTask.class, new Action<PrefixHeaderFileGenerateTask>() {
                                @Override
                                public void execute(PrefixHeaderFileGenerateTask prefixHeaderFileGenerateTask) {
                                    prefixHeaderFileGenerateTask.setPrefixHeaderFile(dependentSourceSet.getPrefixHeaderFile());
                                    prefixHeaderFileGenerateTask.setHeaders(dependentSourceSet.getPreCompiledHeaders());
                                }
                            });
                        }
                    }
                });
            }
        }

        @Mutate
        void configurePreCompiledHeaderCompileTasks(CollectionBuilder<NativeBinarySpecInternal> binaries, final ServiceRegistry serviceRegistry, final PreCompiledHeaderTransformContainer pchTransformContainer, final @Path("buildDir") File buildDir) {
            binaries.all(new Action<NativeBinarySpecInternal>() {
                @Override
                public void execute(final NativeBinarySpecInternal nativeBinarySpec) {
                    for (final LanguageTransform<?, ?> transform : pchTransformContainer) {
                        nativeBinarySpec.getSource().withType(transform.getSourceSetType(), new Action<LanguageSourceSet>() {
                            @Override
                            public void execute(final LanguageSourceSet languageSourceSet) {
                                final DependentSourceSet dependentSourceSet = (DependentSourceSet) languageSourceSet;
                                if (CollectionUtils.isNotEmpty(dependentSourceSet.getPreCompiledHeaders())) {
                                    final SourceTransformTaskConfig taskConfig = transform.getTransformTask();
                                    String pchTaskName = String.format("%s%s%sPreCompiledHeader", taskConfig.getTaskPrefix(), StringUtils.capitalize(nativeBinarySpec.getName()), StringUtils.capitalize(dependentSourceSet.getName()));
                                    nativeBinarySpec.getTasks().create(pchTaskName, taskConfig.getTaskType(), new Action<DefaultTask>() {
                                        @Override
                                        public void execute(DefaultTask task) {
                                            taskConfig.configureTask(task, nativeBinarySpec, dependentSourceSet);
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            });
        }

        @Mutate
        public void applyHeaderSourceSetConventions(CollectionBuilder<ComponentSpec> componentSpecs) {
            componentSpecs.afterEach(new Action<ComponentSpec>() {
                @Override
                public void execute(ComponentSpec componentSpec) {
                    DomainObjectSet<LanguageSourceSet> functionalSourceSet = componentSpec.getSource();
                    for (HeaderExportingSourceSet headerSourceSet : functionalSourceSet.withType(HeaderExportingSourceSet.class)) {
                        // Only apply default locations when none explicitly configured
                        if (headerSourceSet.getExportedHeaders().getSrcDirs().isEmpty()) {
                            headerSourceSet.getExportedHeaders().srcDir(String.format("src/%s/headers", componentSpec.getName()));
                        }

                        headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSrcDirs());
                        headerSourceSet.getImplicitHeaders().include("**/*.h");
                    }
                }
            });
        }

        private void maybeSetSourceDir(SourceDirectorySet sourceSet, Task task, String propertyName) {
            Object value = task.property(propertyName);
            if (value != null) {
                sourceSet.srcDir(value);
            }
        }
    }

    private static class DefaultRepositories extends DefaultPolymorphicDomainObjectContainer<ArtifactRepository> implements Repositories {
        private DefaultRepositories(final Instantiator instantiator, final FileResolver fileResolver, final Action<PrebuiltLibrary> binaryFactory) {
            super(ArtifactRepository.class, instantiator, new ArtifactRepositoryNamer());
            registerFactory(PrebuiltLibraries.class, new NamedDomainObjectFactory<PrebuiltLibraries>() {
                public PrebuiltLibraries create(String name) {
                    return instantiator.newInstance(DefaultPrebuiltLibraries.class, name, instantiator, fileResolver, binaryFactory);
                }
            });
        }
    }

    private static class ArtifactRepositoryNamer implements Namer<ArtifactRepository> {
        public String determineName(ArtifactRepository object) {
            return object.getName();
        }
    }
}