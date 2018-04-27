/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.util.Path;

class DefaultNestedBuild implements NestedBuildState {
    private final BuildDefinition buildDefinition;
    private final NestedBuildFactory nestedBuildFactory;
    private final BuildStateListener buildStateListener;
    private final BuildIdentifier buildIdentifier;
    private SettingsInternal settings;

    DefaultNestedBuild(BuildIdentifier buildIdentifier, BuildDefinition buildDefinition, NestedBuildFactory nestedBuildFactory, BuildStateListener buildStateListener) {
        this.buildIdentifier = buildIdentifier;
        this.buildDefinition = buildDefinition;
        this.nestedBuildFactory = nestedBuildFactory;
        this.buildStateListener = buildStateListener;
    }

    @Override
    public <T> T run(Transformer<T, ? super BuildController> buildAction) {
        GradleLauncher gradleLauncher = nestedBuildFactory.nestedInstance(buildDefinition, buildIdentifier);
        GradleBuildController buildController = new GradleBuildController(gradleLauncher);
        try {
            final GradleInternal gradle = buildController.getGradle();
            gradle.rootProject(new Action<Project>() {
                @Override
                public void execute(Project rootProject) {
                    settings = gradle.getSettings();
                    buildStateListener.projectsKnown(DefaultNestedBuild.this);
                }
            });
            return buildAction.transform(buildController);
        } finally {
            buildController.stop();
        }
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public boolean isImplicitBuild() {
        return true;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        if (settings == null) {
            throw new IllegalStateException("Settings not loaded yet.");
        }
        return settings;
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return getLoadedSettings().getGradle().getRootProject().getProjectRegistry().getProject(projectPath.getPath()).getIdentityPath();
    }
}
