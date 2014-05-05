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

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.LibraryContainer;
import org.gradle.runtime.base.internal.DefaultLibraryContainer;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.ModelFinalizer;
import org.gradle.model.ModelRule;
import org.gradle.model.ModelRules;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.internal.DefaultBuildTypeContainer;
import org.gradle.nativebinaries.internal.DefaultExecutableContainer;
import org.gradle.nativebinaries.internal.DefaultFlavorContainer;
import org.gradle.nativebinaries.internal.NativeLibraryFactory;
import org.gradle.nativebinaries.internal.configure.*;
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver;
import org.gradle.nativebinaries.platform.PlatformContainer;
import org.gradle.nativebinaries.platform.internal.DefaultPlatformContainer;
import org.gradle.nativebinaries.toolchain.internal.DefaultToolChainRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolChainRegistryInternal;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeComponentModelPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ProjectConfigurationActionContainer configurationActions;
    private final ModelRules modelRules;
    private final NativeDependencyResolver resolver;
    private final FileResolver fileResolver;

    @Inject
    public NativeComponentModelPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions, ModelRules modelRules,
                                      NativeDependencyResolver resolver, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
        this.modelRules = modelRules;
        this.resolver = resolver;
        this.fileResolver = fileResolver;
    }

    public void apply(final ProjectInternal project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(LanguageBasePlugin.class);

        modelRules.register("toolChains", ToolChainRegistryInternal.class, factory(DefaultToolChainRegistry.class));
        modelRules.register("platforms", PlatformContainer.class, factory(DefaultPlatformContainer.class));
        modelRules.register("buildTypes", BuildTypeContainer.class, factory(DefaultBuildTypeContainer.class));
        modelRules.register("flavors", FlavorContainer.class, factory(DefaultFlavorContainer.class));

        project.getModelRegistry().create("repositories", Arrays.asList("flavors", "platforms", "buildTypes"), new RepositoriesFactory(instantiator, fileResolver));

        modelRules.rule(new CreateDefaultPlatform());
        modelRules.rule(new CreateDefaultBuildTypes());
        modelRules.rule(new CreateDefaultFlavors());
        modelRules.rule(new AddDefaultToolChainsIfRequired());
        modelRules.rule(new CreateNativeBinaries(instantiator, project, resolver));
        // TODO:DAZ Push this down to LanguageBasePlugin (but first need to deal with ClassDirectoryBinary)
        modelRules.rule(new AttachBinariesToLifecycle());

        DefaultLibraryContainer libraries = (DefaultLibraryContainer) project.getExtensions().getByType(LibraryContainer.class);
        libraries.registerFactory(NativeLibrary.class, new NativeLibraryFactory(instantiator, project));
        // TODO:DAZ Decide if we want implicit library types or not, and implement properly
        libraries.registerDefaultFactory(new NativeLibraryFactory(instantiator, project));

        project.getExtensions().create(
                "executables",
                DefaultExecutableContainer.class,
                instantiator,
                project
        );

        configurationActions.add(Actions.composite(
                new ConfigureGeneratedSourceSets(),
                new ApplySourceSetConventions()
        ));
    }

    private static class AddDefaultToolChainsIfRequired extends ModelFinalizer {
        @SuppressWarnings("UnusedDeclaration")
        void createDefaultToolChain(ToolChainRegistryInternal toolChains) {
            if (toolChains.isEmpty()) {
                toolChains.addDefaultToolChains();
            }
        }
    }

    private static class AttachBinariesToLifecycle extends ModelRule {
        @SuppressWarnings("UnusedDeclaration")
        void attach(TaskContainer tasks, BinaryContainer binaries) {
            Task assembleTask = tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME);
            for (ProjectNativeBinary nativeBinary : binaries.withType(ProjectNativeBinary.class)) {
                if (nativeBinary.isBuildable()) {
                    assembleTask.dependsOn(nativeBinary);
                }
            }
        }
    }

    private <T> Factory<T> factory(Class<T> type) {
        return new InstantiatingFactory<T>(type, instantiator);
    }

    private static class InstantiatingFactory<T> implements Factory<T> {
        private final Class<T> type;
        private final Instantiator instantiator;

        public InstantiatingFactory(Class<T> type, Instantiator instantiator) {
            this.type = type;
            this.instantiator = instantiator;
        }

        public T create() {
            return instantiator.newInstance(type, instantiator);
        }
    }
}