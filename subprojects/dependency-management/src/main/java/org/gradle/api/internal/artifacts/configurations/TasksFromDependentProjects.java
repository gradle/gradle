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
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.util.Set;

class TasksFromDependentProjects extends AbstractTaskDependency {

    private final String taskName;
    private final String configurationName;
    private final TaskDependencyChecker checker;

    public TasksFromDependentProjects(String taskName, String configurationName) {
        this(taskName, configurationName, new TaskDependencyChecker());
    }

    public TasksFromDependentProjects(String taskName, String configurationName, TaskDependencyChecker checker) {
        this.taskName = taskName;
        this.configurationName = configurationName;
        this.checker = checker;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        Project thisProject = context.getTask().getProject();
        Set<Task> tasksWithName = thisProject.getRootProject().getTasksByName(taskName, true);
        for (Task nextTask : tasksWithName) {
            boolean isDependency = checker.isDependent(thisProject, configurationName, nextTask.getProject());
            if (isDependency) {
                context.add(nextTask);
            }
        }
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
