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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This converts Gradle's projects into ProjectView objects. These can be safely
 * reused unlike Gradle's projects.
 *
 * @author mhunsicker
 */
public class ProjectConverter {
    private List<ProjectView> rootLevelResultingProjects = new ArrayList<ProjectView>();
    private HashMap<Project, ProjectView> projectMap = new HashMap<Project, ProjectView>();
    private final Logger logger = Logging.getLogger(ProjectConverter.class);

    public ProjectConverter() {
    }

    /**
       Call this to convert the projects.

       @param  rootProject the root project.
    */
    public List<ProjectView> convertProjects(Project rootProject) {
        rootLevelResultingProjects.clear();
        projectMap.clear();

        addRootLevelProject(rootProject);

        buildDependencies();

        return rootLevelResultingProjects;
    }

    /**
       This adds the specified poject as a root level projects. It then adds
       all tasks and recursively adds all sub projects.

       @param  rootLevelProject a root level project.
    */
    public void addRootLevelProject(Project rootLevelProject) {
        ProjectView rootLevelProjectView = new ProjectView(rootLevelProject.getName(), rootLevelProject.getBuildFile());
        projectMap.put(rootLevelProject, rootLevelProjectView);

        rootLevelResultingProjects.add(rootLevelProjectView);

        addSubProjects(rootLevelProject, rootLevelProjectView, 1);

        addTasks(rootLevelProject, rootLevelProjectView);

        rootLevelProjectView.sortSubProjectsAndTasks();
    }

    /**
       Adds all sub projects of the specifed GradleProject.

       @param  parentProject        the source parent project. Where we get the sub projects from.
       @param  parentProjectView  the destination of the sub projects from parentProject.
    */
    private void addSubProjects(Project parentProject, ProjectView parentProjectView, int currentDepth) {
        Set<Project> subProjects = parentProject.getSubprojects();
        Iterator<Project> iterator = subProjects.iterator();
        while (iterator.hasNext()) {
            Project subProject = iterator.next();
            int depth = subProject.getDepth();
            if (depth == currentDepth)   //at the root, we seem to be getting all projects regardless of their depth (that is we'll get root:subproject1:subproject2 as the root's subproject). We'll ignore these and then add them to our hierarchy when we get to the correct depth.
            {
                ProjectView projectView = new ProjectView(subProject.getName(), subProject.getBuildFile());
                projectMap.put(subProject, projectView);

                parentProjectView.addSubProject(projectView);

                addTasks(subProject, projectView);

                projectView.sortSubProjectsAndTasks();

                addSubProjects(subProject, projectView, currentDepth + 1);
            }
        }
    }

    /**
       Adds the tasks from the project to the GradleProject.

       @param  project       the source parent project. Where we get the sub projects from.
       @param  projectView the destination of the tasks from project.
    */
    private void addTasks(Project project, ProjectView projectView) {
        List<String> defaultTasks = project.getDefaultTasks();
        Set<Task> tasks = project.getTasks().getAll();
        for (Task task : tasks) {
            String taskName = task.getName();

            boolean isDefault = defaultTasks.contains(taskName);

            projectView.createTask(taskName, task.getDescription(), isDefault);
        }
    }

    /**
       This sets the dependencies on the ProjectViews. We ask the gradle projects
       for the dependencies and then convert them to ProjectViews. Obviously,
       this must be done after converting all Projects to ProjectViews.
    */
    private void buildDependencies() {
        Iterator<Project> projectIterator = projectMap.keySet().iterator();
        while (projectIterator.hasNext()) {
            Project project = projectIterator.next();

            ProjectView projectView = projectMap.get(project);

            List<ProjectView> projectViewList = getProjectViews(project.getDependsOnProjects());

            projectView.setDependsOnProjects(projectViewList);
        }
    }

    /**
       Converts a set of projects to the existing project views. This does not
       actually instantiate new ProjectView objects.
    */
    private List<ProjectView> getProjectViews(Set<Project> projects) {
        List<ProjectView> views = new ArrayList<ProjectView>();

        Iterator<Project> projectIterator = projects.iterator();
        while (projectIterator.hasNext()) {
            Project project = projectIterator.next();
            ProjectView projectView = projectMap.get(project);
            if (projectView == null)
                logger.error("Missing project: " + project.getName());
            else
                views.add(projectView);
        }

        return views;
    }
}