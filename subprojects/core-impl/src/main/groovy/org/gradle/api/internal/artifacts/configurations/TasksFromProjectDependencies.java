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

import java.util.Set;

class TasksFromProjectDependencies extends AbstractTaskDependency {
    private final String taskName;
    private final DependencySet dependencies;

    public TasksFromProjectDependencies(String taskName, DependencySet dependencies) {
        this.taskName = taskName;
        this.dependencies = dependencies;
    }

    public void resolve(TaskDependencyResolveContext context) {
        resolveProjectDependencies(context, dependencies.withType(ProjectDependency.class));
    }

    void resolveProjectDependencies(TaskDependencyResolveContext context, Set<ProjectDependency> projectDependencies) {
        for (ProjectDependency projectDependency : projectDependencies) {
            //in configure-on-demand we don't know if the project was configured, hence explicit evaluate.
            // Not especially tidy, we should clean this up while working on new configuration model.
            ((ProjectInternal) projectDependency.getDependencyProject()).evaluate();

            Task nextTask = projectDependency.getDependencyProject().getTasks().findByName(taskName);
            if (nextTask != null) {
                context.add(nextTask);
            }
        }
    }

    public String getTaskName() {
        return taskName;
    }
}
