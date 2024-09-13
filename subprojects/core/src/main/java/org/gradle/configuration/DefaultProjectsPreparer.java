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

import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.ProjectsEvaluatedNotifier;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.operations.BuildOperationRunner;

public class DefaultProjectsPreparer implements ProjectsPreparer {
    private final BuildOperationRunner buildOperationRunner;
    private final ProjectConfigurer projectConfigurer;
    private final BuildType buildType;
    private final BuildModelParameters buildModelParameters;

    public DefaultProjectsPreparer(
        ProjectConfigurer projectConfigurer,
        BuildType buildType,
        BuildModelParameters buildModelParameters,
        BuildOperationRunner buildOperationRunner
    ) {
        this.projectConfigurer = projectConfigurer;
        this.buildType = buildType;
        this.buildModelParameters = buildModelParameters;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void prepareProjects(GradleInternal gradle) {
        if (buildModelParameters.isConfigureOnDemand() && gradle.isRootBuild()) {
            return;
        }

        if (buildModelParameters.isIsolatedProjects()) {
            if (buildType != BuildType.TASKS && gradle.isRootBuild()) {
                // Keep it incremental if we are not running tasks
                return;
            }
            projectConfigurer.configureHierarchyInParallel(gradle.getRootProject());
        } else {
            projectConfigurer.configureHierarchy(gradle.getRootProject());
        }
        new ProjectsEvaluatedNotifier(buildOperationRunner).notify(gradle);
    }

}
