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

import org.gradle.util.GUtil;

import java.io.Serializable;

/**
 * Analog to gradle's Task but more light-weight and better suited for using the gradle API from an IDE plugin. It is also easily serializable for passing across a socket. A task is something you can
 * execute and is part of a project. This is immutable and ultimately comes from gradle files.
 */
public class TaskView implements Comparable<TaskView>, Serializable {
    private ProjectView project;
    private String name;
    private String description;
    private boolean isDefault;
    //whether or not this is one of potentially many default tasks for its project.

    /**
     * Instantiates an immutable view of a task. This is only meant to be called internally whenever generating a hierarchy of projects and tasks.
     */
    /*package*/ TaskView(ProjectView project, String name, String description, boolean isDefault) {
        this.project = project;
        this.name = name;
        this.isDefault = isDefault;
        this.description = GUtil.elvis(description, "");
    }

    public ProjectView getProject() {
        return project;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasDescription() {
        return !description.equals("");
    }

    /**
     * returns whether or not this is a default task for its parent project. These are defined by specifying
     *
     * defaultTasks 'task name'
     *
     * in the gradle file. There can be multiple default tasks.
     *
     * @return true if its a default task, false if not.
     */
    public boolean isDefault() {
        return isDefault;
    }

    public int compareTo(TaskView otherTask) {
        //sort by project name first, then by task name.
        int projectComparison = project.compareTo(otherTask.getProject());
        if (projectComparison != 0) {
            return projectComparison;
        }

        return name.compareTo(otherTask.name);
    }

    public String toString() {
        return name;
    }

    /**
     * This generates this task's full name. This is a colon-separated string of this task and its parent projects.
     *
     * Example: root_project:sub_project:sub_sub_project:task.
     */
    public String getFullTaskName() {
        return project.getFullProjectName() + ':' + name;
    }
}
