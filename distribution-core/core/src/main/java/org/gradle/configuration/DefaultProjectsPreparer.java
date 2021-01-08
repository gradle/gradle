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
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.util.IncubationLogger;

public class DefaultProjectsPreparer implements ProjectsPreparer {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectConfigurer projectConfigurer;
    private final ModelConfigurationListener modelConfigurationListener;

    public DefaultProjectsPreparer(
            ProjectConfigurer projectConfigurer,
            ModelConfigurationListener modelConfigurationListener,
            BuildOperationExecutor buildOperationExecutor
    ) {
        this.projectConfigurer = projectConfigurer;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        if (gradle.getStartParameter().isConfigureOnDemand()) {
            IncubationLogger.incubatingFeatureUsed("Configuration on demand");
            projectConfigurer.configure(gradle.getRootProject());
        } else {
            projectConfigurer.configureHierarchy(gradle.getRootProject());
            new ProjectsEvaluatedNotifier(buildOperationExecutor).notify(gradle);
        }

        modelConfigurationListener.onConfigure(gradle);
    }
}
