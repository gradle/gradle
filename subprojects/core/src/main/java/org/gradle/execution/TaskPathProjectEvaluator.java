/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.BuildCancelledException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildCancellationToken;

public class TaskPathProjectEvaluator implements ProjectConfigurer {
    private final BuildCancellationToken cancellationToken;

    public TaskPathProjectEvaluator(BuildCancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    @Override
    public void configure(ProjectInternal project) {
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException();
        }
        // Need to configure intermediate parent projects for configure-on-demand
        ProjectInternal parentProject = project.getParent();
        if (parentProject != null) {
            configure(parentProject);
        }
        project.getOwner().ensureConfigured();
    }

    @Override
    public void configureFully(ProjectInternal project) {
        configure(project);
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException();
        }
        project.getOwner().ensureTasksDiscovered();
    }

    @Override
    public void configureHierarchy(ProjectInternal project) {
        configure(project);
        for (Project sub : project.getSubprojects()) {
            configure((ProjectInternal) sub);
        }
    }

    @Override
    public void configureHierarchyFully(ProjectInternal project) {
        configureFully(project);
        for (Project sub : project.getSubprojects()) {
            configureFully((ProjectInternal) sub);
        }
    }
}
