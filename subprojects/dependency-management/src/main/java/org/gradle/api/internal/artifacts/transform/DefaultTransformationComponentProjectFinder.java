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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.build.BuildStateRegistry;

public class DefaultTransformationComponentProjectFinder implements TransformationComponentProjectFinder {
    private final BuildStateRegistry buildStateRegistry;
    private final ProjectFinder projectFinder;

    public DefaultTransformationComponentProjectFinder(BuildStateRegistry buildStateRegistry, ProjectFinder projectFinder) {
        this.buildStateRegistry = buildStateRegistry;
        this.projectFinder = projectFinder;
    }

    @Override
    public ProjectInternal find(ProjectComponentIdentifier projectComponentIdentifier) {
        String projectPath = projectComponentIdentifier.getProjectPath();
        if (projectComponentIdentifier.getBuild().isCurrentBuild()) {
            return projectFinder.findProject(projectPath);
        }
        GradleInternal build = buildStateRegistry.getIncludedBuild(projectComponentIdentifier.getBuild()).getConfiguredBuild();
        return build.getRootProject().findProject(projectPath);
    }
}
