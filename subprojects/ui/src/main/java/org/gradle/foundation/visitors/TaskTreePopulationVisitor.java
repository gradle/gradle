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
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * This visits each project and task in a hierarchical manner. This visitor is specifically meant to walk the projects first and tasks second for the purpose of populating a tree where the
 * projects/subprojects are first and the tasks are second.
 */
public class TaskTreePopulationVisitor {
    public interface Visitor<P, T> {
        /*
           This is called for each project.
         @param  project    the project
         @param indexOfProject
         @param  parentProjectObject whatever you handed back from a prior call to
                 visitProject if this is a sub project. Otherwise, it'll be whatever
                 was passed into the visitPojectsAndTasks function.
           @return an object that will be handed back to you for each of this
                   project's tasks.
        */

        public P visitProject(ProjectView project, int indexOfProject, P parentProjectObject);

        /*
           This is called for each task.

         @param  task               the task
         @param indexOfTask
         @param  tasksProject       the project for this task
         @param  userProjectObject  whatever you returned from the parent project's visitProject
        */

        public T visitTask(TaskView task, int indexOfTask, ProjectView tasksProject, P userProjectObject);

        /*
           This is called when a project has been visited completely and is just a
           notification giving you an opportunity to do whatever you like.
           This is possibly where you want to delete any nodes that we didn't
           visit.

           @param  parentProjectObject the object that represents the parent of
                                       the project and task objects below
           @param  projectObjects      a list of whatever you returned from visitProject
           @param  taskObjects         a list of whatever you returned from visitTask
        */

        public void completedVisitingProject(P parentProjectObject, List<P> projectObjects, List<T> taskObjects);
    }

    /*
       This visitor will visit each project, sub-project and task that was discovered
       by the GradleHelper. This is useful for building a list or tree of
       projects and tasks.

       This is the same as the other version of visitProjectsAndTasks except this
       one visits everything.
    */

    public static <P, T> void visitProjectAndTasks(List<ProjectView> projects, Visitor<P, T> visitor,
                                                   P rootProjectObject) {
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

    public static <P, T> void visitProjectAndTasks(List<ProjectView> projects, Visitor<P, T> visitor,
                                                   ProjectAndTaskFilter filter, P rootProjectObject) {
        List<P> userProjectObjects = visitProjects(visitor, filter, projects, rootProjectObject, new AlphabeticalProjectNameComparator(), new AlphabeticalTaskNameComparator());

        //notify the visitation of the root projects. There are no tasks for this one, but there are projects.
        visitor.completedVisitingProject(rootProjectObject, userProjectObjects, Collections.<T>emptyList());
    }

    private static <P, T> List<P> visitProjects(Visitor<P, T> visitor, ProjectAndTaskFilter filter,
                                                List<ProjectView> sourceProjects, P parentProjectObject, Comparator<ProjectView> projectSorter, Comparator<TaskView> taskSorter) {
        List<P> projectObjects = new ArrayList<P>();

        sourceProjects = CollectionUtils.sort(sourceProjects, projectSorter);  //make a copy because we're going to sort them.

        Iterator<ProjectView> iterator = sourceProjects.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            ProjectView project = iterator.next();

            if (filter.doesAllowProject(project)) {
                P userProjectObject = visitor.visitProject(project, index, parentProjectObject);
                projectObjects.add(userProjectObject);

                //visit sub projects
                List<P> subProjectObjects = visitProjects(visitor, filter, project.getSubProjects(), userProjectObject, projectSorter, taskSorter);

                //visit tasks. Notice that we pass in the number of subprojects as a starting index. This is so they'll come afterwards.
                List<T> taskObjects = visitTasks(visitor, filter, project, subProjectObjects.size(), userProjectObject, taskSorter);

                visitor.completedVisitingProject(userProjectObject, subProjectObjects, taskObjects);
            }
            index++;
        }

        return projectObjects;
    }

    /*
       Add the list of tasks to the parent tree node.
    */

    private static <P, T> List<T> visitTasks(Visitor<P, T> visitor, ProjectAndTaskFilter filter, ProjectView project,
                                             int startingIndex, P userProjectObject, Comparator<TaskView> taskSorter) {
        List<T> taskObjects = new ArrayList<T>();
        List<TaskView> tasks = CollectionUtils.sort(project.getTasks(), taskSorter); //make a copy because we're going to sort them

        Iterator<TaskView> iterator = tasks.iterator();
        int index = startingIndex;
        while (iterator.hasNext()) {
            TaskView task = iterator.next();
            if (filter.doesAllowTask(task)) {
                T taskObject = visitor.visitTask(task, index, project, userProjectObject);
                taskObjects.add(taskObject);
            }
            index++;
        }

        return taskObjects;
    }

    /**
     * This comparator sorts project names alphabetically ignoring case.
     */
    public static class AlphabeticalProjectNameComparator implements Comparator<ProjectView> {
        public int compare(ProjectView o1, ProjectView o2) {
            return GUtil.caseInsensitive().compare(o1.getName(), o2.getName());
        }
    }

    /**
     * This comparator sorts task names alphabetically ignoring case.
     */
    public static class AlphabeticalTaskNameComparator implements Comparator<TaskView> {
        public int compare(TaskView o1, TaskView o2) {
            return GUtil.caseInsensitive().compare(o1.getName(), o2.getName());
        }
    }
}
