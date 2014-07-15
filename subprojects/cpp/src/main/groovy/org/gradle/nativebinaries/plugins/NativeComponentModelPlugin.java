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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.*;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.internal.configure.*;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.platform.internal.DefaultPlatformContainer;
import org.gradle.nativebinaries.toolchain.internal.DefaultToolChainRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.ProjectComponentContainer;
import org.gradle.runtime.base.internal.BinaryNamingSchemeBuilder;
import org.gradle.runtime.base.internal.DefaultBinaryNamingSchemeBuilder;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeComponentModelPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ProjectConfigurationActionContainer configurationActions;
    private final FileResolver fileResolver;

    @Inject
    public NativeComponentModelPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
        this.fileResolver = fileResolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        project.getModelRegistry().create(Arrays.asList("flavors", "platforms", "buildTypes"), new RepositoriesFactory("repositories", instantiator, fileResolver));

        ProjectComponentContainer components = project.getExtensions().getByType(ProjectComponentContainer.class);
        components.registerFactory(ProjectNativeExecutable.class, new ProjectNativeExecutableFactory(instantiator, project));
        NamedDomainObjectContainer<ProjectNativeExecutable> nativeExecutables = components.containerWithType(ProjectNativeExecutable.class);

        components.registerFactory(ProjectNativeLibrary.class, new ProjectNativeLibraryFactory(instantiator, project));
        NamedDomainObjectContainer<ProjectNativeLibrary> nativeLibraries = components.containerWithType(ProjectNativeLibrary.class);

        project.getExtensions().create("nativeRuntime", DefaultNativeComponentExtension.class, nativeExecutables, nativeLibraries);

        // TODO:DAZ Remove these: should not pollute the global namespace
        project.getExtensions().add("nativeComponents", components.withType(ProjectNativeComponent.class));
        project.getExtensions().add("executables", nativeExecutables);
        project.getExtensions().add("libraries", nativeLibraries);

        configurationActions.add(Actions.composite(
                new ConfigureGeneratedSourceSets(),
                new ApplyHeaderSourceSetConventions()
        ));
    }

    /**
     * Model rules.
     */
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

        @Mutate
        public void registerExtensions(ExtensionContainer extensions, PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors) {
            extensions.add("platforms", platforms);
            extensions.add("buildTypes", buildTypes);
            extensions.add("flavors", flavors);
        }

        @Mutate
        public void createNativeBinaries(BinaryContainer binaries, ExtensionContainer extensions, ToolChainRegistryInternal toolChains, PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors, ServiceRegistry serviceRegistry, @Path("buildDir") File buildDir) {
            Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            NativeDependencyResolver resolver = serviceRegistry.get(NativeDependencyResolver.class);
            Action<ProjectNativeBinary> configureBinaryAction = new ProjectNativeBinaryInitializer(buildDir);
            Action<ProjectNativeBinary> setToolsAction = new ToolSettingNativeBinaryInitializer(extensions.getByType(LanguageRegistry.class));
            Action<ProjectNativeBinary> initAction = Actions.composite(configureBinaryAction, setToolsAction);
            NativeBinariesFactory factory = new DefaultNativeBinariesFactory(instantiator, initAction, resolver);
            BinaryNamingSchemeBuilder namingSchemeBuilder = new DefaultBinaryNamingSchemeBuilder();
            Action<ProjectNativeComponent> createBinariesAction =
                    new ProjectNativeComponentInitializer(factory, namingSchemeBuilder, toolChains, platforms, buildTypes, flavors);

            ProjectComponentContainer projectComponents = extensions.getByType(ProjectComponentContainer.class);
            for (ProjectNativeComponent component : projectComponents.withType(ProjectNativeComponent.class)) {
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
    }

}