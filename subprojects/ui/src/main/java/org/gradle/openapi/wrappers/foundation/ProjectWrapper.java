/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.wrappers.foundation;

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.openapi.external.foundation.ProjectVersion1;
import org.gradle.openapi.external.foundation.TaskVersion1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of ProjectVersion1 meant to help shield external users from internal changes.
 */
public class ProjectWrapper implements ProjectVersion1 {

    private ProjectView projectView;

    public ProjectWrapper(ProjectView projectView) {
        this.projectView = projectView;
    }

    public String getName() {
        return projectView.getName();
    }

    public File getFile() {
        return projectView.getBuildFile();
    }

    public List<TaskVersion1> getTasks() {
        return TaskWrapper.convertTasks(projectView.getTasks());
    }

    public List<ProjectVersion1> getSubProjects() {
        return convertProjects(projectView.getSubProjects());
    }

    public ProjectVersion1 getParentProject() {
        return new ProjectWrapper(projectView.getParentProject());
    }

    public List<ProjectVersion1> getDependantProjects() {
        return Collections.emptyList();
    }

    public ProjectVersion1 getSubProject(String name) {
        ProjectView subProject = projectView.getSubProject(name);
        if (subProject == null) {
            return null;
        }

        return new ProjectWrapper(subProject);
    }

    public String getFullProjectName() {
        return projectView.getFullProjectName();
    }

    public ProjectVersion1 getSubProjectFromFullPath(String fullProjectName) {
        ProjectView projectFromFullPath = projectView.getSubProjectFromFullPath(fullProjectName);
        if (projectFromFullPath == null) {
            return null;
        }
        return new ProjectWrapper(projectFromFullPath);
    }

    public TaskVersion1 getTask(String name) {
        TaskView taskView = projectView.getTask(name);
        if (taskView == null) {
            return null;
        }
        return new TaskWrapper(taskView);
    }

    /**
     * Builds a list of default tasks. These are defined by specifying
     *
     * defaultTasks 'task name'
     *
     * in the gradle file. There can be multiple default tasks. This only returns default tasks directly for this project and does not return them for subprojects.
     *
     * @return a list of default tasks or an empty list if none exist
     */
    public List<TaskVersion1> getDefaultTasks() {
        return TaskWrapper.convertTasks(projectView.getDefaultTasks());
    }

    public TaskVersion1 getTaskFromFullPath(String fullTaskName) {
        TaskView taskView = projectView.getTaskFromFullPath(fullTaskName);
        if (taskView == null) {
            return null;
        }

        return new TaskWrapper(taskView);
    }

    /**
     * Converts the list of ProjectView objects to ProjectVersion1 objects. It just wraps them.
     *
     * @param projectViewList the source projects
     * @return the projects wrapped in ProjectWrappers.
     */
    public static List<ProjectVersion1> convertProjects(List<ProjectView> projectViewList) {
        List<ProjectVersion1> returnProjects = new ArrayList<ProjectVersion1>();
        if (projectViewList != null) {
            Iterator<ProjectView> projectViewIterator = projectViewList.iterator();
            while (projectViewIterator.hasNext()) {
                ProjectView projectView = projectViewIterator.next();
                returnProjects.add(new ProjectWrapper(projectView));
            }
        }

        return returnProjects;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProjectWrapper)) {
            return false;
        }

        ProjectWrapper otherProjectWrapper = (ProjectWrapper) obj;
        return otherProjectWrapper.projectView.equals(projectView);
    }

    @Override
    public int hashCode() {
        return projectView.hashCode();
    }

    @Override
    public String toString() {
        return projectView.toString();
    }
}
