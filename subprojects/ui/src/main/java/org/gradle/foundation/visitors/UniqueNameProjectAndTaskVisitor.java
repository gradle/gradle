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
package org.gradle.foundation.visitors;

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This visitor builds up a list of unqiuely named projects and tasks. The projects will be their full path, so they're all unique.
 */
public class UniqueNameProjectAndTaskVisitor implements AllProjectsAndTasksVisitor.Visitor<Object, Object> {
    private List<String> taskNames = new ArrayList<String>();
    private List<String> projectNames = new ArrayList<String>();

    public List<String> getTaskNames() {
        return taskNames;
    }

    public List<String> getProjectNames() {
        return projectNames;
    }

    public List<String> getSortedTaskNames() {
        return CollectionUtils.sort(taskNames);
    }

    public List<String> getSortedProjectNames() {
        return CollectionUtils.sort(projectNames);
    }

    /*
    This is called for each project.
    @param project             the project
    @param parentProjectObject whatever you handed back from a prior call to
                               visitProject if this is a sub project. Otherwise,
                               it'll be whatever was passed into the
                               visitPojectsAndTasks function.
    @return always null
    */

    public Object visitProject(ProjectView project, Object parentProjectObject) {
        String name = project.getFullProjectName();
        if (!projectNames.contains(name)) {
            projectNames.add(name);
        }

        return null;
    }

    /*
    This is called for each task.
    @param task              the task
    @param tasksProject      the project for this task
    @param userProjectObject always null.
    */

    public Object visitTask(TaskView task, ProjectView tasksProject, Object userProjectObject) {
        String name = task.getName();
        if (!taskNames.contains(name)) {
            taskNames.add(name);
        }

        return null;
    }
}
