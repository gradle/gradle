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
package org.gradle.foundation;

import org.gradle.util.GUtil;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Analog to gradle's Project but more light-weight and is better suited for using the gradle API from an IDE plugin. It is also easily serializable for passing across a socket. A project is a
 * collection of source files that have tasks associated with them. The tasks build the project. Projects can contain other projects. This is immutable and ultimately comes from gradle files.
 */
public class ProjectView implements Comparable<ProjectView>, Serializable {
    private final String name;
    private final ProjectView parentProject;
    // It is null for the root project.
    private final List<ProjectView> subProjects = new ArrayList<ProjectView>();
    private final List<TaskView> tasks = new ArrayList<TaskView>();
    private final List<ProjectView> dependsOnProjects = new ArrayList<ProjectView>();

    private final File buildFile;
    private final String description;

    /**
     * Instantiates an immutable view of a project. This is only meant to be called internally whenever generating a hierarchy of projects and tasks.
     */
    /*package*/ ProjectView(ProjectView parentProject, String name, File buildFile, String description) {
        this.parentProject = parentProject;
        this.name = name;
        this.buildFile = buildFile;
        this.description = GUtil.elvis(description, "");
        if (parentProject != null) {
            parentProject.addSubProject(this);
        }
    }

    public String getName() {
        return name;
    }

    public File getBuildFile() {
        return buildFile;
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        return name;
    }

    public ProjectView getParentProject() {
        return parentProject;
    }

    public int compareTo(ProjectView otherProject) {
        return name.compareTo(otherProject.name);
    }

    /**
     * creates a task for this project. This is only meant to be called internally whenever generating a hierachy of projects and tasks.
     */
    /*package*/ void createTask(String name, String description, boolean isDefault) {
        TaskView taskView = new TaskView(this, name, description, isDefault);
        tasks.add(taskView);
    }

    /**
     * Adds the specified project as a sub project of this project. This is only meant to be called internally whenever generating a hierachy of projects and tasks.
     */
    /*package*/ void addSubProject(ProjectView subProject) {
        subProjects.add(subProject);
    }

    /**
     * Sets the project that this project depends on. This is only meant to be called internally whenever generating a hierachy of projects and tasks.
     */
    /*package*/ void setDependsOnProjects(List<ProjectView> newDependsOnProjects) {
        if (newDependsOnProjects == null) {
            return;
        }

        this.dependsOnProjects.clear();
        this.dependsOnProjects.addAll(newDependsOnProjects);
    }

    public List<ProjectView> getDependsOnProjects() {
        return dependsOnProjects;
    }

    public List<TaskView> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public List<ProjectView> getSubProjects() {
        return Collections.unmodifiableList(subProjects);
    }

    public void sortSubProjectsAndTasks() {
        Collections.sort(tasks);
        Collections.sort(subProjects);
    }

    public ProjectView getSubProject(String name) {
        Iterator<ProjectView> iterator = subProjects.iterator();
        while (iterator.hasNext()) {
            ProjectView subProject = iterator.next();
            if (name.equals(subProject.getName())) {
                return subProject;
            }
        }

        return null;
    }

    public TaskView getTask(String name) {
        Iterator<TaskView> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            TaskView task = iterator.next();
            if (name.equals(task.getName())) {
                return task;
            }
        }

        return null;
    }

    public ProjectView getSubProjectFromFullPath(String fullProjectName) {
        if (fullProjectName == null) {
            return null;
        }

        PathParserPortion portion = new PathParserPortion(fullProjectName);

        ProjectView subProject = getSubProject(portion.getFirstPart());

        if (!portion.hasRemainder()) //if we have no remainder, then the path is just a sub project's name. We're done (even if subProject is null).
        {
            return subProject;
        }

        if (subProject == null) {
            return null;
        }   //the path may be invalid

        return subProject.getSubProjectFromFullPath(portion.getRemainder());
    }

    /**
     * This gets the task based on the given full path. This recursively calls this same function with sub projects until it finds the task or no matches are found.
     *
     * @param fullTaskName the full task name (root_project:sub_project:sub_sub_project:task.).
     * @return the task or null if not found.
     */
    public TaskView getTaskFromFullPath(String fullTaskName) {
        if (fullTaskName == null) {
            return null;
        }

        PathParserPortion portion = new PathParserPortion(fullTaskName);
        if (!portion.hasRemainder()) //if we have no remainder, then this is for a task.
        {
            return getTask(portion.getFirstPart());
        }

        ProjectView subProject = getSubProject(portion.getFirstPart());
        if (subProject == null) {
            return null;
        }

        //let the sub project figure it out.
        return subProject.getTaskFromFullPath(portion.getRemainder());
    }

    /**
     * This generates this project's full name. This is a colon-separated string of this project and its parent projects.
     *
     * Example: root_project:sub_project:sub_sub_project.
     */
    public String getFullProjectName() {
        ProjectView ancestorProject = getParentProject();
        if (ancestorProject == null) {
            return "";
        } //if we're the root, our full project name is nothing.

        StringBuilder builder = new StringBuilder(name);
        while (ancestorProject != null && ancestorProject.getParentProject() != null)   //we don't want to include the 'root' project
        {
            builder.insert(0, ancestorProject.getName() + ':');
            ancestorProject = ancestorProject.getParentProject();
        }

        return builder.toString();
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
    public List<TaskView> getDefaultTasks() {
        List<TaskView> defaultTasks = new ArrayList<TaskView>();

        Iterator<TaskView> taskIterator = tasks.iterator();
        while (taskIterator.hasNext()) {
            TaskView taskView = taskIterator.next();
            if (taskView.isDefault()) {
                defaultTasks.add(taskView);
            }
        }

        return defaultTasks;
    }
}
