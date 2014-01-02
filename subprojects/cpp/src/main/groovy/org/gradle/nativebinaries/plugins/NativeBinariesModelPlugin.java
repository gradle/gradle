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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.BinaryContainer;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.model.ModelFinalizer;
import org.gradle.model.ModelRule;
import org.gradle.model.ModelRules;
import org.gradle.nativebinaries.BuildTypeContainer;
import org.gradle.nativebinaries.FlavorContainer;
import org.gradle.nativebinaries.internal.DefaultBuildTypeContainer;
import org.gradle.nativebinaries.internal.DefaultExecutableContainer;
import org.gradle.nativebinaries.internal.DefaultFlavorContainer;
import org.gradle.nativebinaries.internal.DefaultLibraryContainer;
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
public class NativeBinariesModelPlugin implements Plugin<ProjectInternal> {

    private final Instantiator instantiator;
    private final ProjectConfigurationActionContainer configurationActions;
    private final ModelRules modelRules;
    private final NativeDependencyResolver resolver;
    private final FileResolver fileResolver;

    @Inject
    public NativeBinariesModelPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions, ModelRules modelRules,
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
        modelRules.rule(new CloseBinariesForTasks());

        project.getExtensions().create(
                "executables",
                DefaultExecutableContainer.class,
                instantiator,
                project
        );
        project.getExtensions().create(
                "libraries",
                DefaultLibraryContainer.class,
                instantiator,
                project
        );

        // TODO:DAZ Lazy configuration actions: need a better way to accomplish these.
        configurationActions.add(Actions.composite(
                new ConfigureGeneratedSourceSets(),
                new ApplySourceSetConventions()
        ));
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class CloseBinariesForTasks extends ModelRule {
        void closeBinariesForTasks(TaskContainer tasks, BinaryContainer binaries) {
            // nothing needed here
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class AddDefaultToolChainsIfRequired extends ModelFinalizer {
        void createDefaultToolChain(ToolChainRegistryInternal toolChains) {
            if (toolChains.isEmpty()) {
                toolChains.addDefaultToolChains();
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