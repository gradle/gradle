/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.initialization.ProjectAccessListener;

import java.util.Set;

class TasksFromProjectDependencies extends AbstractTaskDependency {
    private final String taskName;
    private final DependencySet dependencies;
    private final ProjectAccessListener projectAccessListener;

    public TasksFromProjectDependencies(String taskName, DependencySet dependencies, ProjectAccessListener projectAccessListener) {
        this.taskName = taskName;
        this.dependencies = dependencies;
        this.projectAccessListener = projectAccessListener;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        resolveProjectDependencies(context, dependencies.withType(ProjectDependency.class));
    }

    void resolveProjectDependencies(TaskDependencyResolveContext context, Set<ProjectDependency> projectDependencies) {
        for (ProjectDependency projectDependency : projectDependencies) {
            projectAccessListener.beforeResolvingProjectDependency((ProjectInternal) projectDependency.getDependencyProject());

            Task nextTask = projectDependency.getDependencyProject().getTasks().findByName(taskName);
            if (nextTask != null && context.getTask() != nextTask) {
                context.add(nextTask);
            }
        }
    }

    public String getTaskName() {
        return taskName;
    }
}
