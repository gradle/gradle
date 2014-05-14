/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation;

import org.gradle.api.Project;
import org.gradle.api.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This converts Gradle's projects into ProjectView objects. These can be safely reused unlike Gradle's projects.
 */
public class ProjectConverter {
    private List<ProjectView> rootLevelResultingProjects = new ArrayList<ProjectView>();

    /**
     * Call this to convert the projects.
     *
     * @param rootProject the root project.
     */
    public List<ProjectView> convertProjects(Project rootProject) {
        rootLevelResultingProjects.clear();

        addRootLevelProject(rootProject);

        return rootLevelResultingProjects;
    }

    /**
     * This adds the specified project as a root level projects. It then adds all tasks and recursively adds all sub projects.
     *
     * @param rootLevelProject a root level project.
     */
    public void addRootLevelProject(Project rootLevelProject) {
        ProjectView rootLevelProjectView = new ProjectView(null, rootLevelProject.getName(), rootLevelProject.getBuildFile(), rootLevelProject.getDescription());

        rootLevelResultingProjects.add(rootLevelProjectView);

        addSubProjects(rootLevelProject, rootLevelProjectView);

        addTasks(rootLevelProject, rootLevelProjectView);

        rootLevelProjectView.sortSubProjectsAndTasks();
    }

    /**
     * Adds all sub projects of the specified GradleProject.
     *
     * @param parentProject the source parent project. Where we get the sub projects from.
     * @param parentProjectView the destination of the sub projects from parentProject.
     */
    private void addSubProjects(Project parentProject, ProjectView parentProjectView) {
        Collection<Project> subProjects = parentProject.getChildProjects().values();
        for (Project subProject : subProjects) {
            ProjectView projectView = new ProjectView(parentProjectView, subProject.getName(), subProject.getBuildFile(), subProject.getDescription());

            addTasks(subProject, projectView);

            projectView.sortSubProjectsAndTasks();

            addSubProjects(subProject, projectView);
        }
    }

    /**
     * Adds the tasks from the project to the GradleProject.
     *
     * @param project the source parent project. Where we get the sub projects from.
     * @param projectView the destination of the tasks from project.
     */
    private void addTasks(Project project, ProjectView projectView) {
        List<String> defaultTasks = project.getDefaultTasks();
        for (Task task : project.getTasks()) {
            String taskName = task.getName();

            boolean isDefault = defaultTasks.contains(taskName);

            projectView.createTask(taskName, task.getDescription(), isDefault);
        }
    }
}