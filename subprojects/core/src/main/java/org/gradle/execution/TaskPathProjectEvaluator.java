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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.initialization.BuildCancellationToken;

import java.util.ArrayDeque;
import java.util.Queue;

public class TaskPathProjectEvaluator implements ProjectConfigurer {
    private final BuildCancellationToken cancellationToken;

    public TaskPathProjectEvaluator(BuildCancellationToken cancellationToken) {
        this.cancellationToken = cancellationToken;
    }

    @Override
    public void configureFully(ProjectState projectState) {
        projectState.ensureConfigured();
        if (cancellationToken.isCancellationRequested()) {
            throw new BuildCancelledException();
        }
        projectState.ensureTasksDiscovered();
    }

    @Override
    public void configureHierarchy(ProjectState projectState) {
        Queue<ProjectState> queue = new ArrayDeque<>();
        queue.add(projectState);

        while (!queue.isEmpty()) {
            ProjectState current = queue.remove();
            current.ensureConfigured();
            queue.addAll(current.getChildProjects());
        }
    }
}
