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
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.composite.ChildBuildRegisteringSettingsLoader;
import org.gradle.internal.composite.CommandLineIncludedBuildSettingsLoader;
import org.gradle.internal.composite.CompositeBuildSettingsLoader;

public class DefaultSettingsLoaderFactory implements SettingsLoaderFactory {
    private final SettingsProcessor settingsProcessor;
    private final BuildStateRegistry buildRegistry;
    private final ProjectStateRegistry projectRegistry;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradlePropertiesController gradlePropertiesController;
    private final BuildIncluder buildIncluder;

    public DefaultSettingsLoaderFactory(
        SettingsProcessor settingsProcessor,
        BuildStateRegistry buildRegistry,
        ProjectStateRegistry projectRegistry,
        BuildLayoutFactory buildLayoutFactory,
        GradlePropertiesController gradlePropertiesController,
        BuildIncluder buildIncluder
    ) {
        this.settingsProcessor = settingsProcessor;
        this.buildRegistry = buildRegistry;
        this.projectRegistry = projectRegistry;
        this.buildLayoutFactory = buildLayoutFactory;
        this.gradlePropertiesController = gradlePropertiesController;
        this.buildIncluder = buildIncluder;
    }

    @Override
    public SettingsLoader forTopLevelBuild() {
        return new CompositeBuildSettingsLoader(
            new ChildBuildRegisteringSettingsLoader(
                new CommandLineIncludedBuildSettingsLoader(
                    defaultSettingsLoader()
                ),
                buildRegistry,
                buildIncluder),
            buildRegistry);
    }

    @Override
    public SettingsLoader forNestedBuild() {
        return new ChildBuildRegisteringSettingsLoader(
            defaultSettingsLoader(),
            buildRegistry,
            buildIncluder);
    }

    private SettingsLoader defaultSettingsLoader() {
        return new SettingsAttachingSettingsLoader(
            new DefaultSettingsLoader(
                settingsProcessor,
                buildLayoutFactory,
                gradlePropertiesController
            ),
            projectRegistry
        );
    }
}
