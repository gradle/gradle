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
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.configuration.project.ProjectConfigurationActionContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LanguageBasePlugin;
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

    @Inject
    public NativeBinariesModelPlugin(Instantiator instantiator, ProjectConfigurationActionContainer configurationActions, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.configurationActions = configurationActions;
        this.fileResolver = fileResolver;
    }

    public void apply(final Project project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(LanguageBasePlugin.class);

        project.getExtensions().create(
                "toolChains",
                DefaultToolChainRegistry.class,
                instantiator
        );
        project.getExtensions().create(
                "targetPlatforms",
                DefaultPlatformContainer.class,
                instantiator
        );
        project.getExtensions().create(
                "buildTypes",
                DefaultBuildTypeContainer.class,
                instantiator
        );
        project.getExtensions().create(
                "executables",
                DefaultExecutableContainer.class,
                instantiator
        );
        project.getExtensions().create(
                "libraries",
                DefaultLibraryContainer.class,
                instantiator,
                fileResolver
        );

        // TODO:DAZ Lazy configuration actions: need a better way to accomplish these.
        configurationActions.add(Actions.composite(
                new ApplySourceSetConventions(),
                new CreateDefaultToolChain(),
                new CreateDefaultPlatform(),
                new CreateDefaultBuildTypes(),
                new CreateDefaultFlavors(),
                new CreateNativeBinaries(instantiator))
        );
    }

}