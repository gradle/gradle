/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.util.Path;

/**
 * Resolves tasks from this project or other projects in a build, given some task path.
 */
public class ProjectScopedTaskResolver implements TaskResolver {

    private final BuildScopedTaskResolver buildTaskResolver;
    private final ProjectIdentity currentProject;
    private final TaskContainer taskContainer;

    public ProjectScopedTaskResolver(
        BuildScopedTaskResolver buildTaskResolver,
        ProjectIdentity currentProject,
        TaskContainer taskContainer
    ) {
        this.buildTaskResolver = buildTaskResolver;
        this.currentProject = currentProject;
        this.taskContainer = taskContainer;
    }

    @Override
    public Task resolveTask(Path path) {
        String targetTaskName = path.getName();
        if (targetTaskName == null) {
            assert path == Path.ROOT;
            throw new IllegalArgumentException("The root path is not a valid task path");
        }

        if (!path.isAbsolute() && path.segmentCount() == 1) {
            return taskContainer.getByName(targetTaskName);
        }

        Path targetProjectPath = path.getParent() != null
            ? currentProject.getProjectPath().absolutePath(path.getParent())
            : Path.ROOT;

        if (targetProjectPath.equals(currentProject.getProjectPath())) {
            return taskContainer.getByName(targetTaskName);
        }

        // The task is in another project.
        Path absoluteTaskPath = targetProjectPath.child(targetTaskName);
        return buildTaskResolver.resolveTask(absoluteTaskPath);
    }

}
