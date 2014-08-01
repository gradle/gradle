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
package org.gradle.nativebinaries.plugins;

import org.gradle.api.*;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.HeaderExportingSourceSet;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.*;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.internal.configure.*;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.platform.internal.DefaultPlatformContainer;
import org.gradle.nativebinaries.toolchain.internal.DefaultToolChainRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ComponentSpecContainer;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

import javax.inject.Inject;
import java.io.File;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeComponentModelPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    @Inject
    public NativeComponentModelPlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        project.getModelRegistry().create(new RepositoriesFactory("repositories", instantiator, fileResolver));

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
        ToolChainRegistryInternal toolChains(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultToolChainRegistry.class, instantiator);
        }

        @Model
        PlatformContainer platforms(ServiceRegistry serviceRegistry) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
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
        public void registerExtensions(ExtensionContainer extensions, PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors) {
            extensions.add("platforms", platforms);
            extensions.add("buildTypes", buildTypes);
            extensions.add("flavors", flavors);
        }

        @Mutate
        public void createNativeBinaries(BinaryContainer binaries, NamedDomainObjectSet<NativeComponentSpec> nativeComponents,
                                         LanguageRegistry languages, ToolChainRegistryInternal toolChains,
                                         PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors,
                                         ServiceRegistry serviceRegistry, @Path("buildDir") File buildDir) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            Action<NativeBinarySpec> configureBinaryAction = new NativeBinarySpecInitializer(buildDir);
            Action<NativeBinarySpec> setToolsAction = new ToolSettingNativeBinaryInitializer(languages);
            Action<NativeBinarySpec> initAction = Actions.composite(configureBinaryAction, setToolsAction, new MarkBinariesBuildable());
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
        public void createDefaultToolChain(ToolChainRegistryInternal toolChains) {
            if (toolChains.isEmpty()) {
                toolChains.addDefaultToolChains();
            }
        }

        @Finalize
        public void createDefaultPlatforms(PlatformContainer platforms) {
            if (platforms.isEmpty()) {
                platforms.create("current");
            }
        }

        @Finalize
        public void createDefaultPlatforms(BuildTypeContainer buildTypes) {
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
            ToolChainInternal toolChainInternal = (ToolChainInternal) nativeBinarySpec.getToolChain();
            boolean canBuild = toolChainInternal.select(nativeBinarySpec.getTargetPlatform()).isAvailable();
            ((NativeBinarySpecInternal) nativeBinarySpec).setBuildable(canBuild);
        }
    }
}