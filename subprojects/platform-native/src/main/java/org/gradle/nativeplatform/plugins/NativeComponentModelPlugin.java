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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Namer;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.resolve.ProjectModelResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.nativeplatform.internal.DependentSourceSetInternal;
import org.gradle.model.Defaults;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.BuildTypeContainer;
import org.gradle.nativeplatform.FlavorContainer;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.NativeDependencySet;
import org.gradle.nativeplatform.NativeExecutableBinarySpec;
import org.gradle.nativeplatform.NativeExecutableSpec;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.nativeplatform.Repositories;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.TargetedNativeComponent;
import org.gradle.nativeplatform.internal.DefaultBuildTypeContainer;
import org.gradle.nativeplatform.internal.DefaultFlavor;
import org.gradle.nativeplatform.internal.DefaultFlavorContainer;
import org.gradle.nativeplatform.internal.DefaultNativeExecutableBinarySpec;
import org.gradle.nativeplatform.internal.DefaultNativeExecutableSpec;
import org.gradle.nativeplatform.internal.DefaultNativeLibrarySpec;
import org.gradle.nativeplatform.internal.DefaultSharedLibraryBinarySpec;
import org.gradle.nativeplatform.internal.DefaultStaticLibraryBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativeComponents;
import org.gradle.nativeplatform.internal.NativeDependentBinariesResolutionStrategy;
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal;
import org.gradle.nativeplatform.internal.NativePlatformResolver;
import org.gradle.nativeplatform.internal.SharedLibraryBinarySpecInternal;
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal;
import org.gradle.nativeplatform.internal.TargetedNativeComponentInternal;
import org.gradle.nativeplatform.internal.configure.NativeComponentRules;
import org.gradle.nativeplatform.internal.pch.PchEnabledLanguageTransform;
import org.gradle.nativeplatform.internal.prebuilt.DefaultPrebuiltLibraries;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryInitializer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.PrefixHeaderFileGenerateTask;
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.DefaultNativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.Platform;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.SourceComponentSpec;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.HasIntermediateOutputsComponentSpec;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeComponentModelPlugin implements Plugin<ProjectInternal> {
    private final Instantiator instantiator;
    private CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    @Inject
    public NativeComponentModelPlugin(Instantiator instantiator, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.instantiator = instantiator;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);

        project.getExtensions().create(BuildTypeContainer.class, "buildTypes", DefaultBuildTypeContainer.class, instantiator, collectionCallbackActionDecorator);
        project.getExtensions().create(FlavorContainer.class, "flavors", DefaultFlavorContainer.class, instantiator, collectionCallbackActionDecorator);
        project.getExtensions().create(NativeToolChainRegistry.class, "toolChains", DefaultNativeToolChainRegistry.class, instantiator, collectionCallbackActionDecorator);
    }

    static class Rules extends RuleSource {
        @ComponentType
        void nativeExecutable(TypeBuilder<NativeExecutableSpec> builder) {
            builder.defaultImplementation(DefaultNativeExecutableSpec.class);
        }

        @ComponentType
        void nativeLibrary(TypeBuilder<NativeLibrarySpec> builder) {
            builder.defaultImplementation(DefaultNativeLibrarySpec.class);
        }

        @ComponentType
        void registerTargetedNativeComponent(TypeBuilder<TargetedNativeComponent> builder) {
            builder.internalView(TargetedNativeComponentInternal.class);
        }

        @ComponentType
        void registerNativeComponent(TypeBuilder<NativeComponentSpec> builder) {
            builder.internalView(HasIntermediateOutputsComponentSpec.class);
        }

        @Model
        Repositories repositories(ServiceRegistry serviceRegistry,
                                  FlavorContainer flavors,
                                  PlatformContainer platforms,
                                  BuildTypeContainer buildTypes,
                                  CollectionCallbackActionDecorator callbackActionDecorator) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            ObjectFactory sourceDirectorySetFactory = serviceRegistry.get(ObjectFactory.class);
            NativePlatforms nativePlatforms = serviceRegistry.get(NativePlatforms.class);
            FileCollectionFactory fileCollectionFactory = serviceRegistry.get(FileCollectionFactory.class);
            Action<PrebuiltLibrary> initializer = new PrebuiltLibraryInitializer(instantiator, fileCollectionFactory, nativePlatforms, platforms.withType(NativePlatform.class), buildTypes, flavors);
            DomainObjectCollectionFactory domainObjectCollectionFactory = serviceRegistry.get(DomainObjectCollectionFactory.class);
            return new DefaultRepositories(instantiator, sourceDirectorySetFactory, initializer, callbackActionDecorator, domainObjectCollectionFactory);
        }

        @Model
        NativeToolChainRegistryInternal toolChains(ExtensionContainer extensionContainer) {
            return Cast.cast(NativeToolChainRegistryInternal.class, extensionContainer.getByType(NativeToolChainRegistry.class));
        }

        @Model
        BuildTypeContainer buildTypes(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(BuildTypeContainer.class);
        }

        @Model
        FlavorContainer flavors(ExtensionContainer extensionContainer) {
            return extensionContainer.getByType(FlavorContainer.class);
        }

        @Mutate
        public void registerNativePlatformResolver(PlatformResolvers resolvers, ServiceRegistry serviceRegistry) {
            resolvers.register(serviceRegistry.get(NativePlatformResolver.class));
        }

        @Defaults
        public void registerFactoryForCustomNativePlatforms(PlatformContainer platforms, final Instantiator instantiator) {
            NamedDomainObjectFactory<NativePlatform> nativePlatformFactory = new NativePlatformNamedDomainObjectFactory(instantiator);

            platforms.registerFactory(NativePlatform.class, nativePlatformFactory);

            platforms.registerFactory(Platform.class, nativePlatformFactory);
        }

        @ComponentType
        void registerSharedLibraryBinaryType(TypeBuilder<SharedLibraryBinarySpec> builder) {
            builder.defaultImplementation(DefaultSharedLibraryBinarySpec.class);
            builder.internalView(SharedLibraryBinarySpecInternal.class);
        }

        @ComponentType
        void registerStaticLibraryBinaryType(TypeBuilder<StaticLibraryBinarySpec> builder) {
            builder.defaultImplementation(DefaultStaticLibraryBinarySpec.class);
            builder.internalView(StaticLibraryBinarySpecInternal.class);
        }

        @ComponentType
        void registerNativeExecutableBinaryType(TypeBuilder<NativeExecutableBinarySpec> builder) {
            builder.defaultImplementation(DefaultNativeExecutableBinarySpec.class);
            builder.internalView(NativeExecutableBinarySpecInternal.class);
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

        @Finalize
        void configureGeneratedSourceSets(@Each LanguageSourceSetInternal languageSourceSet) {
            Task generatorTask = languageSourceSet.getGeneratorTask();
            if (generatorTask != null) {
                languageSourceSet.builtBy(generatorTask);
                maybeSetSourceDir(languageSourceSet.getSource(), generatorTask, "sourceDir");
                if (languageSourceSet instanceof HeaderExportingSourceSet) {
                    maybeSetSourceDir(((HeaderExportingSourceSet) languageSourceSet).getExportedHeaders(), generatorTask, "headerDir");
                }
            }
        }

        @Defaults
        void configurePrefixHeaderFiles(@Each final SourceComponentSpec componentSpec, final @Path("buildDir") File buildDir) {
            componentSpec.getSources().withType(DependentSourceSetInternal.class).afterEach(new DependentSourceSetInternalAction(componentSpec, buildDir));
        }

        @Mutate
        void configurePrefixHeaderGenerationTasks(final TaskContainer tasks, ComponentSpecContainer components) {
            for (final SourceComponentSpec nativeComponentSpec : components.withType(SourceComponentSpec.class).values()) {
                for (final DependentSourceSetInternal dependentSourceSet : nativeComponentSpec.getSources().withType(DependentSourceSetInternal.class).values()) {
                    if (dependentSourceSet.getPrefixHeaderFile() != null) {
                        String taskName = "generate" + StringUtils.capitalize(nativeComponentSpec.getName()) + StringUtils.capitalize(dependentSourceSet.getName()) + "PrefixHeaderFile";
                        tasks.create(taskName, PrefixHeaderFileGenerateTask.class, new PrefixHeaderFileGenerateTaskAction(dependentSourceSet));
                    }
                }
            }
        }

        @Mutate
        void configurePreCompiledHeaderCompileTasks(final TaskContainer tasks, BinaryContainer binaries, final LanguageTransformContainer languageTransforms, final ServiceRegistry serviceRegistry) {
            for (final NativeBinarySpecInternal nativeBinarySpec : binaries.withType(NativeBinarySpecInternal.class)) {
                for (final PchEnabledLanguageTransform<?> transform : languageTransforms.withType(PchEnabledLanguageTransform.class)) {
                    nativeBinarySpec.getInputs().withType(transform.getSourceSetType(), new LanguageSourceSetAction(nativeBinarySpec, transform, tasks, serviceRegistry));
                }
            }
        }

        private void maybeSetSourceDir(SourceDirectorySet sourceSet, Task task, String propertyName) {
            Object value = task.property(propertyName);
            if (value != null) {
                sourceSet.srcDir(value);
            }
        }

        @BinaryTasks
        public void sharedLibraryTasks(ModelMap<Task> tasks, final SharedLibraryBinarySpecInternal binary) {
            String taskName = binary.getNamingScheme().getTaskName("link");
            tasks.create(taskName, LinkSharedLibrary.class, new LinkSharedLibraryAction(binary));
        }

        @BinaryTasks
        public void staticLibraryTasks(ModelMap<Task> tasks, final StaticLibraryBinarySpecInternal binary) {
            String taskName = binary.getNamingScheme().getTaskName("create");
            tasks.create(taskName, CreateStaticLibrary.class, new CreateStaticLibraryAction(binary));
        }

        @BinaryTasks
        public void executableTasks(ModelMap<Task> tasks, final NativeExecutableBinarySpecInternal executableBinary) {
            NativeComponents.createExecutableTask(executableBinary, executableBinary.getExecutable().getFile());
        }

        @Defaults
        public void createBuildDependentComponentsTasks(ModelMap<Task> tasks, ComponentSpecContainer components, BinaryContainer binaries) {
            NativeComponents.createBuildDependentComponentsTasks(tasks, components);
        }

        @BinaryTasks
        public void createBuildDependentBinariesTasks(ModelMap<Task> tasks, NativeBinarySpecInternal nativeBinary) {
            NativeComponents.createBuildDependentBinariesTasks(nativeBinary, nativeBinary.getNamingScheme());
        }

        @Finalize
        public void wireBuildDependentTasks(ModelMap<Task> tasks, BinaryContainer binaries, DependentBinariesResolver dependentsResolver, ServiceRegistry serviceRegistry) {
            NativeComponents.wireBuildDependentTasks(tasks, binaries, dependentsResolver, serviceRegistry.get(ProjectModelResolver.class));
        }

        /**
         * Can't use @BinaryTasks because the binary is not _built-by_ the install task, but it is associated with it. Rule is called multiple times, so need to check for task existence before
         * creating.
         */
        @Defaults
        void createInstallTasks(ModelMap<Task> tasks, BinaryContainer binaries) {
            for (NativeExecutableBinarySpecInternal binary : binaries.withType(NativeExecutableBinarySpecInternal.class).values()) {
                NativeComponents.createInstallTask(binary, binary.getInstallation(), binary.getExecutable(), binary.getNamingScheme());
            }
        }

        @Finalize
        void applyHeaderSourceSetConventions(@Each HeaderExportingSourceSet headerSourceSet) {
            // Only apply default locations when none explicitly configured
            if (headerSourceSet.getExportedHeaders().getSourceDirectories().isEmpty()) {
                headerSourceSet.getExportedHeaders().srcDir("src/" + headerSourceSet.getParentName() + "/headers");
            }

            headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSourceDirectories());
            headerSourceSet.getImplicitHeaders().include("**/*.h");
        }

        @Finalize
        void createBinaries(@Each TargetedNativeComponentInternal nativeComponent,
                            PlatformResolvers platforms,
                            BuildTypeContainer buildTypes,
                            FlavorContainer flavors,
                            ServiceRegistry serviceRegistry
        ) {
            NativePlatforms nativePlatforms = serviceRegistry.get(NativePlatforms.class);
            NativeDependencyResolver nativeDependencyResolver = serviceRegistry.get(NativeDependencyResolver.class);
            FileCollectionFactory fileCollectionFactory = serviceRegistry.get(FileCollectionFactory.class);
            NativeComponentRules.createBinariesImpl(nativeComponent, platforms, buildTypes, flavors, nativePlatforms, nativeDependencyResolver, fileCollectionFactory);
        }

        @Defaults
        void registerNativeDependentBinariesResolutionStrategy(DependentBinariesResolver resolver, ServiceRegistry serviceRegistry) {
            ProjectRegistry<ProjectInternal> projectRegistry = Cast.uncheckedCast(serviceRegistry.get(ProjectRegistry.class));
            ProjectModelResolver projectModelResolver = serviceRegistry.get(ProjectModelResolver.class);
            resolver.register(new NativeDependentBinariesResolutionStrategy(projectRegistry, projectModelResolver));
        }

        private static class CreateStaticLibraryAction implements Action<CreateStaticLibrary> {
            private final StaticLibraryBinarySpecInternal binary;

            public CreateStaticLibraryAction(StaticLibraryBinarySpecInternal binary) {
                this.binary = binary;
            }

            @Override
            public void execute(CreateStaticLibrary task) {
                task.setDescription("Creates " + binary.getDisplayName());
                task.getToolChain().set(binary.getToolChain());
                task.getTargetPlatform().set(binary.getTargetPlatform());
                task.getOutputFile().set(binary.getStaticLibraryFile());
                task.getStaticLibArgs().set(binary.getStaticLibArchiver().getArgs());
            }
        }

        private static class MyBinaryLibs extends NativeComponents.BinaryLibs {
            public MyBinaryLibs(SharedLibraryBinarySpecInternal binary) {
                super(binary);
            }

            @Override
            protected FileCollection getFiles(NativeDependencySet nativeDependencySet) {
                return nativeDependencySet.getLinkFiles();
            }
        }

        private static class LinkSharedLibraryAction implements Action<LinkSharedLibrary> {
            private final SharedLibraryBinarySpecInternal binary;

            public LinkSharedLibraryAction(SharedLibraryBinarySpecInternal binary) {
                this.binary = binary;
            }

            @Override
            public void execute(LinkSharedLibrary linkTask) {
                linkTask.setDescription("Links " + binary.getDisplayName());
                linkTask.getToolChain().set(binary.getToolChain());
                linkTask.getTargetPlatform().set(binary.getTargetPlatform());
                linkTask.getLinkedFile().set(binary.getSharedLibraryFile());
                linkTask.getInstallName().set(binary.getSharedLibraryFile().getName());
                linkTask.getLinkerArgs().set(binary.getLinker().getArgs());
                linkTask.getImportLibrary().set(binary.getSharedLibraryLinkFile());

                linkTask.lib(new MyBinaryLibs(binary));
            }
        }

        private static class DefaultTaskAction implements Action<DefaultTask> {
            private final SourceTransformTaskConfig pchTransformTaskConfig;
            private final NativeBinarySpecInternal nativeBinarySpec;
            private final DependentSourceSet dependentSourceSet;
            private final ServiceRegistry serviceRegistry;

            public DefaultTaskAction(SourceTransformTaskConfig pchTransformTaskConfig, NativeBinarySpecInternal nativeBinarySpec, DependentSourceSet dependentSourceSet, ServiceRegistry serviceRegistry) {
                this.pchTransformTaskConfig = pchTransformTaskConfig;
                this.nativeBinarySpec = nativeBinarySpec;
                this.dependentSourceSet = dependentSourceSet;
                this.serviceRegistry = serviceRegistry;
            }

            @Override
            public void execute(DefaultTask task) {
                pchTransformTaskConfig.configureTask(task, nativeBinarySpec, dependentSourceSet, serviceRegistry);
            }
        }

        private static class LanguageSourceSetAction implements Action<LanguageSourceSet> {
            private final NativeBinarySpecInternal nativeBinarySpec;
            private final PchEnabledLanguageTransform<?> transform;
            private final TaskContainer tasks;
            private final ServiceRegistry serviceRegistry;

            public LanguageSourceSetAction(NativeBinarySpecInternal nativeBinarySpec, PchEnabledLanguageTransform<?> transform, TaskContainer tasks, ServiceRegistry serviceRegistry) {
                this.nativeBinarySpec = nativeBinarySpec;
                this.transform = transform;
                this.tasks = tasks;
                this.serviceRegistry = serviceRegistry;
            }

            @Override
            public void execute(final LanguageSourceSet languageSourceSet) {
                final DependentSourceSet dependentSourceSet = (DependentSourceSet) languageSourceSet;
                if (dependentSourceSet.getPreCompiledHeader() != null) {
                    nativeBinarySpec.addPreCompiledHeaderFor(dependentSourceSet);
                    final SourceTransformTaskConfig pchTransformTaskConfig = transform.getPchTransformTask();
                    String pchTaskName = pchTransformTaskConfig.getTaskPrefix() + StringUtils.capitalize(nativeBinarySpec.getProjectScopedName()) + StringUtils.capitalize(dependentSourceSet.getName()) + "PreCompiledHeader";
                    Task pchTask = tasks.create(pchTaskName, pchTransformTaskConfig.getTaskType(), new DefaultTaskAction(pchTransformTaskConfig, nativeBinarySpec, dependentSourceSet, serviceRegistry));
                    nativeBinarySpec.getTasks().add(pchTask);
                }
            }
        }

        private static class PrefixHeaderFileGenerateTaskAction implements Action<PrefixHeaderFileGenerateTask> {
            private final DependentSourceSetInternal dependentSourceSet;

            public PrefixHeaderFileGenerateTaskAction(DependentSourceSetInternal dependentSourceSet) {
                this.dependentSourceSet = dependentSourceSet;
            }

            @Override
            public void execute(PrefixHeaderFileGenerateTask prefixHeaderFileGenerateTask) {
                prefixHeaderFileGenerateTask.setPrefixHeaderFile(dependentSourceSet.getPrefixHeaderFile());
                prefixHeaderFileGenerateTask.setHeader(dependentSourceSet.getPreCompiledHeader());
            }
        }

        private static class DependentSourceSetInternalAction implements Action<DependentSourceSetInternal> {
            private final SourceComponentSpec componentSpec;
            private final File buildDir;

            public DependentSourceSetInternalAction(SourceComponentSpec componentSpec, File buildDir) {
                this.componentSpec = componentSpec;
                this.buildDir = buildDir;
            }

            @Override
            public void execute(DependentSourceSetInternal dependentSourceSet) {
                if (dependentSourceSet.getPreCompiledHeader() != null) {
                    String prefixHeaderDirName = "tmp/" + componentSpec.getName() + "/" + dependentSourceSet.getName() + "/prefixHeaders";
                    File prefixHeaderDir = new File(buildDir, prefixHeaderDirName);
                    File prefixHeaderFile = new File(prefixHeaderDir, "prefix-headers.h");
                    dependentSourceSet.setPrefixHeaderFile(prefixHeaderFile);
                }
            }
        }

        private static class NativePlatformNamedDomainObjectFactory implements NamedDomainObjectFactory<NativePlatform> {
            private final Instantiator instantiator;

            public NativePlatformNamedDomainObjectFactory(Instantiator instantiator) {
                this.instantiator = instantiator;
            }

            @Override
            public NativePlatform create(String name) {
                return instantiator.newInstance(DefaultNativePlatform.class, name);
            }
        }
    }

    private static class DefaultRepositories extends DefaultPolymorphicDomainObjectContainer<ArtifactRepository> implements Repositories {
        DefaultRepositories(Instantiator instantiator,
                            ObjectFactory objectFactory,
                            Action<PrebuiltLibrary> binaryFactory,
                            CollectionCallbackActionDecorator collectionCallbackActionDecorator,
                            DomainObjectCollectionFactory domainObjectCollectionFactory) {
            super(ArtifactRepository.class, instantiator, new ArtifactRepositoryNamer(), collectionCallbackActionDecorator);
            registerFactory(PrebuiltLibraries.class, new PrebuiltLibrariesNamedDomainObjectFactory(instantiator, objectFactory, binaryFactory, collectionCallbackActionDecorator, domainObjectCollectionFactory));
        }

        private static class PrebuiltLibrariesNamedDomainObjectFactory implements NamedDomainObjectFactory<PrebuiltLibraries> {
            private final Instantiator instantiator;
            private final ObjectFactory objectFactory;
            private final Action<PrebuiltLibrary> binaryFactory;
            private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
            private final DomainObjectCollectionFactory domainObjectCollectionFactory;

            public PrebuiltLibrariesNamedDomainObjectFactory(Instantiator instantiator, ObjectFactory objectFactory, Action<PrebuiltLibrary> binaryFactory, CollectionCallbackActionDecorator collectionCallbackActionDecorator, DomainObjectCollectionFactory domainObjectCollectionFactory) {
                this.instantiator = instantiator;
                this.objectFactory = objectFactory;
                this.binaryFactory = binaryFactory;
                this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
                this.domainObjectCollectionFactory = domainObjectCollectionFactory;
            }

            @Override
            public PrebuiltLibraries create(String name) {
                return instantiator.newInstance(DefaultPrebuiltLibraries.class, name, instantiator, objectFactory, binaryFactory, collectionCallbackActionDecorator, domainObjectCollectionFactory);
            }
        }
    }

    private static class ArtifactRepositoryNamer implements Namer<ArtifactRepository> {
        @Override
        public String determineName(ArtifactRepository object) {
            return object.getName();
        }
    }

}
