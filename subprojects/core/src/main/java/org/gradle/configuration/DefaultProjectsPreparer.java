/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.initialization.ProjectsEvaluatedNotifier;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.operations.BuildOperationExecutor;

public class DefaultProjectsPreparer implements ProjectsPreparer {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectConfigurer projectConfigurer;
    private final BuildModelParameters buildModelParameters;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildStateRegistry buildStateRegistry;

    public DefaultProjectsPreparer(
        ProjectConfigurer projectConfigurer,
        BuildModelParameters buildModelParameters,
        ModelConfigurationListener modelConfigurationListener,
        BuildOperationExecutor buildOperationExecutor,
        BuildStateRegistry buildStateRegistry
    ) {
        this.projectConfigurer = projectConfigurer;
        this.buildModelParameters = buildModelParameters;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        if (!buildModelParameters.isConfigureOnDemand() || !gradle.isRootBuild()) {
            projectConfigurer.configureHierarchy(gradle.getRootProject());
            new ProjectsEvaluatedNotifier(buildOperationExecutor).notify(gradle);
        }

        if (gradle.isRootBuild()) {
            // Make root build substitutions available
            buildStateRegistry.afterConfigureRootBuild();
        }

        modelConfigurationListener.onConfigure(gradle);
    }
}
