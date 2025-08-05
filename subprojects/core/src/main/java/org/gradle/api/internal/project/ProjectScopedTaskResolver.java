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
import org.gradle.internal.build.BuildState;
import org.gradle.util.Path;

/**
 * Resolves tasks from this project or other projects in a build, given some task path.
 * <p>
 * Note: This resolver is not IP-safe, as it finds and exposes mutable task
 * instances from other mutable project instances. Avoid this resolver if possible.
 */
public class ProjectScopedTaskResolver implements TaskResolver {

    private final TaskContainer taskContainer;
    private final BuildState build;
    private final ProjectIdentity currentProject;

    public ProjectScopedTaskResolver(
        TaskContainer taskContainer,
        BuildState build,
        ProjectIdentity currentProject
    ) {
        this.taskContainer = taskContainer;
        this.build = build;
        this.currentProject = currentProject;
    }

    @Override
    public Task resolveTask(String pathStr) {
        Path path = Path.path(pathStr);
        if (!path.isAbsolute() && path.getParent() == null) {
            return taskContainer.getByName(pathStr);
        }

        Path projectPath = path.getParent();
        Path actualProjectPath = projectPath != null
            ? currentProject.getProjectPath().absolutePath(projectPath)
            : Path.ROOT;

        ProjectState projectState = build.getProjects().getProject(actualProjectPath);
        projectState.ensureTasksDiscovered();

        return projectState.getMutableModel().getTasks().getByName(path.getName());
    }

}
