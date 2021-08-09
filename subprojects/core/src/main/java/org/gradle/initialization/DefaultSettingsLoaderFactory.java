/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.configuration.project.BuiltInCommand;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader;
import org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader;
import org.gradle.internal.composite.CompositeBuildSettingsLoader;

import java.util.List;

public class DefaultSettingsLoaderFactory implements SettingsLoaderFactory {
    private final SettingsProcessor settingsProcessor;
    private final BuildStateRegistry buildRegistry;
    private final ProjectStateRegistry projectRegistry;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradlePropertiesController gradlePropertiesController;
    private final BuildIncluder buildIncluder;
    private final InitScriptHandler initScriptHandler;
    private final List<BuiltInCommand> builtInCommands;

    public DefaultSettingsLoaderFactory(
        SettingsProcessor settingsProcessor,
        BuildStateRegistry buildRegistry,
        ProjectStateRegistry projectRegistry,
        BuildLayoutFactory buildLayoutFactory,
        GradlePropertiesController gradlePropertiesController,
        BuildIncluder buildIncluder,
        InitScriptHandler initScriptHandler,
        List<BuiltInCommand> builtInCommands
    ) {
        this.settingsProcessor = settingsProcessor;
        this.buildRegistry = buildRegistry;
        this.projectRegistry = projectRegistry;
        this.buildLayoutFactory = buildLayoutFactory;
        this.gradlePropertiesController = gradlePropertiesController;
        this.buildIncluder = buildIncluder;
        this.initScriptHandler = initScriptHandler;
        this.builtInCommands = builtInCommands;
    }

    @Override
    public SettingsLoader forTopLevelBuild() {
        return new GradlePropertiesHandlingSettingsLoader(
            new InitScriptHandlingSettingsLoader(
                new CompositeBuildSettingsLoader(
                    new ChildBuildRegisteringSettingsLoader(
                        new CommandLineIncludedBuildSettingsLoader(
                            defaultSettingsLoader()
                        ),
                        buildRegistry,
                        buildIncluder),
                    buildRegistry),
                initScriptHandler),
            buildLayoutFactory,
            gradlePropertiesController
        );
    }

    @Override
    public SettingsLoader forNestedBuild() {
        return new GradlePropertiesHandlingSettingsLoader(
            new InitScriptHandlingSettingsLoader(
                new ChildBuildRegisteringSettingsLoader(
                    defaultSettingsLoader(),
                    buildRegistry,
                    buildIncluder),
                initScriptHandler),
            buildLayoutFactory,
            gradlePropertiesController
        );
    }

    private SettingsLoader defaultSettingsLoader() {
        return new SettingsAttachingSettingsLoader(
            new DefaultSettingsLoader(
                settingsProcessor,
                buildLayoutFactory,
                builtInCommands
            ),
            projectRegistry
        );
    }
}
