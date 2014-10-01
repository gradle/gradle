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

import org.gradle.api.*;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.model.*;
import org.gradle.nativeplatform.*;
import org.gradle.nativeplatform.internal.*;
import org.gradle.nativeplatform.internal.configure.*;
import org.gradle.nativeplatform.internal.prebuilt.DefaultPrebuiltLibraries;
import org.gradle.nativeplatform.internal.prebuilt.PrebuiltLibraryInitializer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.internal.DefaultNativeToolChainRegistry;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.PlatformContainer;
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultBinaryNamingSchemeBuilder;
import org.gradle.platform.base.internal.DefaultPlatformContainer;

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
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        ProjectSourceSet sources = project.getExtensions().getByType(ProjectSourceSet.class);
        ComponentSpecContainer components = project.getExtensions().getByType(ComponentSpecContainer.class);
        components.registerFactory(NativeExecutableSpec.class, new NativeExecutableSpecFactory(instantiator, sources, project));
        NamedDomainObjectContainer<NativeExecutableSpec> nativeExecutables = components.containerWithType(NativeExecutableSpec.class);

        components.registerFactory(NativeLibrarySpec.class, new NativeLibrarySpecFactory(instantiator, sources, project));
        NamedDomainObjectContainer<NativeLibrarySpec> nativeLibraries = components.containerWithType(NativeLibrarySpec.class);

        project.getExtensions().create("nativeRuntime", DefaultNativeComponentExtension.class, nativeExecutables, nativeLibraries);

        // TODO:DAZ Remove these: should not pollute the global namespace
        project.getExtensions().add("nativeComponents", components.withType(NativeComponentSpec.class));
        project.getExtensions().add("executables", nativeExecutables);
        project.getExtensions().add("libraries", nativeLibraries);
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    public static class Rules {

        @Model
        Repositories repositories(ServiceRegistry serviceRegistry, FlavorContainer flavors, PlatformContainer platforms, BuildTypeContainer buildTypes) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            Action<PrebuiltLibrary> initializer = new PrebuiltLibraryInitializer(instantiator, platforms.withType(NativePlatform.class), buildTypes, flavors);
            return new DefaultRepositories(instantiator, fileResolver, initializer);
        }

        @Model
        NativeToolChainRegistryInternal toolChains(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultNativeToolChainRegistry.class, instantiator);
        }

        @Model
        BuildTypeContainer buildTypes(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultBuildTypeContainer.class, instantiator);
        }

        @Model
        FlavorContainer flavors(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultFlavorContainer.class, instantiator);
        }

        @Model
        NamedDomainObjectSet<NativeComponentSpec> nativeComponents(ComponentSpecContainer components) {
            return components.withType(NativeComponentSpec.class);
        }

        @Mutate
        public void registerExtensions(ExtensionContainer extensions, BuildTypeContainer buildTypes, FlavorContainer flavors) {
            extensions.add("buildTypes", buildTypes);
            extensions.add("flavors", flavors);
        }

        @Mutate
        public void registerNativePlatformFactory(PlatformContainer platforms, ServiceRegistry serviceRegistry) {
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NamedDomainObjectFactory<NativePlatform> nativePlatformFactory = new NamedDomainObjectFactory<NativePlatform>() {
                public NativePlatform create(String name) {
                    return instantiator.newInstance(DefaultNativePlatform.class, name);
                }
            };

            //TODO freekh: remove cast/this comment when registerDefault exists on interface
            ((DefaultPlatformContainer) platforms).registerDefaultFactory(nativePlatformFactory);
            platforms.registerFactory(NativePlatform.class, nativePlatformFactory);
        }

        @Mutate
        public void createNativeBinaries(BinaryContainer binaries, NamedDomainObjectSet<NativeComponentSpec> nativeComponents,
                                         LanguageRegistry languages, NativeToolChainRegistryInternal toolChains,
                                         PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors,
                                         ServiceRegistry serviceRegistry, @Path("buildDir") File buildDir) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            Action<NativeBinarySpec> configureBinaryAction = new NativeBinarySpecInitializer(buildDir);
            Action<NativeBinarySpec> setToolsAction = new ToolSettingNativeBinaryInitializer(languages);
            Action<NativeBinarySpec> setDefaultTargetsAction = new ToolSettingNativeBinaryInitializer(languages);
            @SuppressWarnings("unchecked") Action<NativeBinarySpec> initAction = Actions.composite(configureBinaryAction, setToolsAction, new MarkBinariesBuildable());
            NativeBinariesFactory factory = new DefaultNativeBinariesFactory(instantiator, initAction, resolver);
            BinaryNamingSchemeBuilder namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder();
            Action<NativeComponentSpec> createBinariesAction =
                    new NativeComponentSpecInitializer(factory, namingSchemeBuilder, toolChains, platforms, buildTypes, flavors);

            for (NativeComponentSpec component : nativeComponents) {
                createBinariesAction.execute(component);
                binaries.addAll(component.getBinaries());
            }
        }

        @Finalize
        public void createDefaultPlatforms(PlatformContainer platforms) {
            if (platforms.withType(NativePlatform.class).isEmpty()) {
                // TODO:DAZ Create a set of known platforms, rather than a single 'default'
                NativePlatform defaultPlatform = platforms.create(NativePlatform.DEFAULT_NAME, NativePlatform.class);
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
        void configureGeneratedSourceSets(ProjectSourceSet sources) {
            for (FunctionalSourceSet functionalSourceSet : sources) {
                for (LanguageSourceSetInternal languageSourceSet : functionalSourceSet.withType(LanguageSourceSetInternal.class)) {
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
        }

        @Finalize
        public void applyHeaderSourceSetConventions(ProjectSourceSet sources) {
            for (FunctionalSourceSet functionalSourceSet : sources) {
                for (HeaderExportingSourceSet headerSourceSet : functionalSourceSet.withType(HeaderExportingSourceSet.class)) {
                    // Only apply default locations when none explicitly configured
                    if (headerSourceSet.getExportedHeaders().getSrcDirs().isEmpty()) {
                        headerSourceSet.getExportedHeaders().srcDir(String.format("src/%s/headers", functionalSourceSet.getName()));
                    }

                    headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSrcDirs());
                    headerSourceSet.getImplicitHeaders().include("**/*.h");
                }
            }
        }

        private void maybeSetSourceDir(SourceDirectorySet sourceSet, Task task, String propertyName) {
            Object value = task.property(propertyName);
            if (value != null) {
                sourceSet.srcDir(value);
            }
        }

    }

    private static class MarkBinariesBuildable implements Action<NativeBinarySpec> {
        public void execute(NativeBinarySpec nativeBinarySpec) {
            NativeToolChainInternal toolChainInternal = (NativeToolChainInternal) nativeBinarySpec.getToolChain();
            boolean canBuild = toolChainInternal.select((NativePlatformInternal) nativeBinarySpec.getTargetPlatform()).isAvailable();
            ((NativeBinarySpecInternal) nativeBinarySpec).setBuildable(canBuild);
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