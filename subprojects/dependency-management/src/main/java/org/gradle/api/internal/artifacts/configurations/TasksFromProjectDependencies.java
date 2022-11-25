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
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Supplier;

class TasksFromProjectDependencies implements TaskDependencyContainerInternal {
    private final String taskName;
    private final TaskDependencyContainerInternal taskDependencyDelegate;

    public TasksFromProjectDependencies(String taskName, Supplier<DependencySet> dependencies, TaskDependencyFactory taskDependencyFactory) {
        this.taskName = taskName;
        this.taskDependencyDelegate = taskDependencyFactory.visitingDependencies(
            context -> resolveProjectDependencies(context, dependencies.get().withType(ProjectDependency.class))
        );
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        taskDependencyDelegate.visitDependencies(context);
    }

    void resolveProjectDependencies(TaskDependencyResolveContext context, Set<ProjectDependency> projectDependencies) {
        for (ProjectDependency projectDependency : projectDependencies) {
            ProjectInternal dependencyProject = (ProjectInternal) projectDependency.getDependencyProject();
            dependencyProject.getOwner().ensureTasksDiscovered();
            Task nextTask = projectDependency.getDependencyProject().getTasks().findByName(taskName);
            if (nextTask != null && context.getTask() != nextTask) {
                context.add(nextTask);
            }
        }
    }

    public String getTaskName() {
        return taskName;
    }

    @Override
    public Set<? extends Task> getDependencies(@Nullable Task task) {
        return taskDependencyDelegate.getDependencies(task);
    }

    @Override
    public Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task) {
        return taskDependencyDelegate.getDependenciesForInternalUse(task);
    }
}
