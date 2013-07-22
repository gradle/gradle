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
import org.gradle.gradleplugin.foundation.filters.AllowAllProjectAndTaskFilter;
import org.gradle.gradleplugin.foundation.filters.ProjectAndTaskFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This visits all projects and their subprojects and tasks in a hierarchal manner. Useful if you need to do some processing with each one.
 */
public class AllProjectsAndTasksVisitor {
    /*
          Visitor allowing you to do whatever you like with a project or task.
    */

    public interface Visitor<P, T> {
        /*
           This is called for each project.
         @param  project    the project
         @param  parentProjectObject whatever you handed back from a prior call to
            visitProject if this is a sub project. Otherwise, it'll be whatever
            was passed into the visitPojectsAndTasks function.
           @return an object that will be handed back to you for each of this
                   project's tasks.
        */

        public P visitProject(ProjectView project, P parentProjectObject);

        /*
           This is called for each task.

         @param  task               the task
         @param  tasksProject       the project for this task
         @param  userProjectObject  whatever you returned from the parent project's visitProject
        */

        public T visitTask(TaskView task, ProjectView tasksProject, P userProjectObject);
    }

    /*
       This visitor will visit each project, sub-project and task that was discovered
       by the GradleHelper. This is useful for building a list or tree of
       projects and tasks.

       This is the same as the other version of visitProjectsAndTasks except this
       one visits everything.
    */

    public static <P, T> void visitProjectAndTasks(List<ProjectView> projects, Visitor<P, T> visitor, P rootProjectObject) {
        visitProjectAndTasks(projects, visitor, new AllowAllProjectAndTaskFilter(), rootProjectObject);
    }

    /*
       This visitor will visit each project, sub-project and task that was discovered
       by the GradleHelper. This is useful for building a list or tree of
       projects and tasks.

       @param  visitor    this notified you of each project and task.
       @param  filter     allows you to skip projects and tasks as specified by the filter.
       @param  rootProjectObject whatever you pass here will be passed to the
                root-level projects as parentProjectObject.
    */

    public static <P, T> void visitProjectAndTasks(List<ProjectView> projects, Visitor<P, T> visitor, ProjectAndTaskFilter filter, P rootProjectObject) {
        visitProjects(visitor, filter, projects, rootProjectObject);
    }

    public static <P, T> List<P> visitProjects(Visitor<P, T> visitor, ProjectAndTaskFilter filter, List<ProjectView> projects, P parentProjectObject) {
        List<P> projectObjects = new ArrayList<P>();

        Iterator<ProjectView> iterator = projects.iterator();
        while (iterator.hasNext()) {
            ProjectView project = iterator.next();

            if (filter.doesAllowProject(project)) {
                P userProjectObject = visitor.visitProject(project, parentProjectObject);
                projectObjects.add(userProjectObject);

                //visit sub projects
                visitProjects(visitor, filter, project.getSubProjects(), userProjectObject);

                //visit tasks
                visitTasks(visitor, filter, project, userProjectObject);
            }
        }

        return projectObjects;
    }

    /*
       Add the list of tasks to the parent tree node.
    */

    private static <P, T> List<T> visitTasks(Visitor<P, T> visitor, ProjectAndTaskFilter filter, ProjectView project, P userProjectObject) {
        List<T> taskObjects = new ArrayList<T>();
        Iterator<TaskView> iterator = project.getTasks().iterator();
        while (iterator.hasNext()) {
            TaskView task = iterator.next();

            if (filter.doesAllowTask(task)) {
                T taskObject = visitor.visitTask(task, project, userProjectObject);
                taskObjects.add(taskObject);
            }
        }

        return taskObjects;
    }
}
