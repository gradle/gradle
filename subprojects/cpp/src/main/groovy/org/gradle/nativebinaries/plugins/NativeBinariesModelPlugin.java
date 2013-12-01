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
import org.gradle.api.Project;
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
import org.gradle.nativebinaries.PlatformContainer;
import org.gradle.nativebinaries.internal.*;
import org.gradle.nativebinaries.internal.configure.*;

import javax.inject.Inject;

/**
 * A plugin that sets up the infrastructure for defining native binaries.
 */
@Incubating
public class NativeBinariesModelPlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final ProjectConfigurationActionContainer configurationActions;
    private final FileResolver fileResolver;
    private final ModelRules modelRules;

    @Inject
    public NativeBinariesModelPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions, FileResolver fileResolver, ModelRules modelRules) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
        this.fileResolver = fileResolver;
        this.modelRules = modelRules;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(LanguageBasePlugin.class);

        modelRules.register("toolChains", ToolChainRegistryInternal.class, new ToolChainFactory(instantiator));
        modelRules.register("platforms", PlatformContainer.class, new PlatformFactory(instantiator));
        modelRules.register("buildTypes", BuildTypeContainer.class, new BuildTypeFactory(instantiator));
        modelRules.register("flavors", FlavorContainer.class, new FlavorFactory(instantiator));

        modelRules.rule(new CreateDefaultPlatform());
        modelRules.rule(new CreateDefaultBuildTypes());
        modelRules.rule(new CreateDefaultFlavors());
        modelRules.rule(new AddDefaultToolChainsIfRequired());
        modelRules.rule(new CreateNativeBinaries(instantiator, (ProjectInternal) project));
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
                fileResolver,
                project
        );

        // TODO:DAZ Lazy configuration actions: need a better way to accomplish these.
        configurationActions.add(Actions.composite(
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

    private static class ToolChainFactory implements Factory<ToolChainRegistryInternal> {
        private final Instantiator instantiator;

        public ToolChainFactory(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        public ToolChainRegistryInternal create() {
            return instantiator.newInstance(DefaultToolChainRegistry.class, instantiator);
        }
    }

    private static class PlatformFactory implements Factory<PlatformContainer> {
        private final Instantiator instantiator;

        private PlatformFactory(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        public PlatformContainer create() {
            return instantiator.newInstance(DefaultPlatformContainer.class, instantiator);
        }
    }

    private static class BuildTypeFactory implements Factory<BuildTypeContainer> {
        private final Instantiator instantiator;

        private BuildTypeFactory(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        public BuildTypeContainer create() {
            return instantiator.newInstance(DefaultBuildTypeContainer.class, instantiator);
        }
    }

    private static class FlavorFactory implements Factory<FlavorContainer> {
        private final Instantiator instantiator;

        private FlavorFactory(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        public FlavorContainer create() {
            return instantiator.newInstance(DefaultFlavorContainer.class, instantiator);
        }
    }
}