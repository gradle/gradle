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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import javax.annotation.Nullable;
import java.util.Set;

class TasksFromDependentProjects implements TaskDependencyContainerInternal {

    private final String taskName;
    private final String configurationName;
    private final TaskDependencyContainerInternal taskDependencyDelegate;

    public TasksFromDependentProjects(String taskName, String configurationName, TaskDependencyFactory taskDependencyFactory) {
        this(taskName, configurationName, new TaskDependencyChecker(), taskDependencyFactory);
    }

    public TasksFromDependentProjects(String taskName, String configurationName, TaskDependencyChecker checker, TaskDependencyFactory taskDependencyFactory) {
        this.taskName = taskName;
        this.configurationName = configurationName;
        this.taskDependencyDelegate = taskDependencyFactory.visitingDependencies(context -> {
            Project thisProject = context.getTask().getProject();
            Set<Task> tasksWithName = thisProject.getRootProject().getTasksByName(taskName, true);
            for (Task nextTask : tasksWithName) {
                if (context.getTask() != nextTask) {
                    boolean isDependency = checker.isDependent(thisProject, configurationName, nextTask.getProject());
                    if (isDependency) {
                        context.add(nextTask);
                    }
                }
            }
        });
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        taskDependencyDelegate.visitDependencies(context);
    }

    @Override
    public Set<? extends Task> getDependencies(@Nullable Task task) {
        return taskDependencyDelegate.getDependencies(task);
    }

    @Override
    public Set<? extends Task> getDependenciesForInternalUse(@Nullable Task task) {
        return taskDependencyDelegate.getDependenciesForInternalUse(task);
    }

    static class TaskDependencyChecker {
        //checks if candidate project is dependent of the origin project with given configuration
        boolean isDependent(Project originProject, String configurationName, Project candidateProject) {
            Configuration configuration = candidateProject.getConfigurations().findByName(configurationName);
            return configuration != null && doesConfigurationDependOnProject(configuration, originProject);
        }

        private static boolean doesConfigurationDependOnProject(Configuration configuration, Project project) {
            Set<ProjectDependency> projectDependencies = configuration.getAllDependencies().withType(ProjectDependency.class);
            for (ProjectDependency projectDependency : projectDependencies) {
                if (projectDependency.getDependencyProject().equals(project)) {
                    return true;
                }
            }
            return false;
        }
    }

    public String getTaskName() {
        return taskName;
    }

    public String getConfigurationName() {
        return configurationName;
    }
}
