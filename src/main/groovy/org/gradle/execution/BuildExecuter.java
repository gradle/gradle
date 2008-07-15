/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.internal.DefaultTask;
import org.gradle.api.internal.project.DefaultProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.*;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;
import org.gradle.api.internal.project.AbstractProject;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class BuildExecuter {
    private static Logger logger = LoggerFactory.getLogger(BuildExecuter.class);

    Dag dag;

    Map<String, Set<Task>> taskCache = new HashMap<String, Set<Task>>();

    public BuildExecuter() {
    }

    public BuildExecuter(Dag dag) {
        this.dag = dag;
    }

    public Boolean execute(String taskName, boolean recursive, Project currentProject, Project rootProject, boolean checkForRebuildDag) {
        assert taskName != null;
        assert currentProject != null;

        logger.info(String.format("++ Executing: %s Recursive:%s Startproject: %s", taskName, recursive, currentProject));

        Set<Task> calledTasks = getTasks(taskName, currentProject, recursive);
        logger.debug("Found tasks: {}", calledTasks);
        if (calledTasks.size() == 0) {           
            throw new UnknownTaskException("No tasks available for " + taskName);
        }
        Clock clock = new Clock();
        dag.reset();
        fillDag(this.dag, calledTasks, rootProject);
        logger.info("Timing: Creating the DAG took " + clock.getTime());
        clock.reset();
        dag.execute();
        logger.info("Timing: Executing the DAG took " + clock.getTime());
        if (!checkForRebuildDag) {
            return null;
        }
        for (Task calledTask : calledTasks) {
            if (!calledTask.isDagNeutral()) {
                return true;
            }
        }
        return false;
    }

    public List unknownTasks(List<String> taskNames, boolean recursive, Project currentProject) {
        List<String> unknownTasks = new ArrayList<String>();
        for (String taskName : taskNames) {
            if (getTasks(taskName, currentProject, recursive).size() == 0) {
                unknownTasks.add(taskName);
            }
        }
        return unknownTasks;
    }

    private Set<Task> getTasks(String primaryTaskName, Project currentProject, boolean recursive) {
        if (taskCache.get(primaryTaskName) == null) {
            taskCache.put(primaryTaskName, currentProject.getTasksByName(primaryTaskName, recursive));
        }
        return taskCache.get(primaryTaskName);
    }

    private void fillDag(Dag dag, Collection<Task> tasks, Project rootProject) {
        for (Task task : tasks) {
            Set<Task> dependsOnTasks = findDependsOnTasks(task, rootProject);
            dag.addTask(task, dependsOnTasks);
            if (dependsOnTasks.size() > 0) {
                logger.debug("Found dependsOn tasks for {}: {}", task, dependsOnTasks);
                fillDag(dag, dependsOnTasks, rootProject);
            } else {
                logger.debug("Found no dependsOn tasks for {}", task);
            }
        }
    }

    private Set<Task> findDependsOnTasks(Task task, Project rootProject) {
        logger.debug("Find dependsOn tasks for {}", task);
        Set<Task> dependsOnTasks = new HashSet<Task>();
        for (Object taskDescriptor : task.getDependsOn()) {
            String absolutePath = absolutePath(task.getProject(), taskDescriptor);
            Path path = new Path(absolutePath);
            DefaultProject project = (DefaultProject) getProjectFromTaskPath(path, rootProject);
            DefaultTask dependsOnTask = (DefaultTask) project.getTasks().get(path.taskName);
            if (dependsOnTask == null)
                throw new UnknownTaskException("Task with path " + taskDescriptor + " could not be found.");
            dependsOnTasks.add(dependsOnTask);
        }
        return dependsOnTasks;
    }

    private String absolutePath(Project project, Object taskDescriptor) {
        if (taskDescriptor instanceof Task) {
            return ((Task) taskDescriptor).getPath();
        }
        return project.absolutePath(taskDescriptor.toString());
    }

    private Project getProjectFromTaskPath(Path path, Project rootProject) {
        try {
            return rootProject.project(path.projectPath);
        } catch (UnknownProjectException e) {
            throw new UnknownTaskException("Task with path " + path + " could not be found!");
        }
    }

    private static class Path {
        private String projectPath;
        private String taskName;

        private Path(String taskPath) {
            int index = taskPath.lastIndexOf(Project.PATH_SEPARATOR);
            if (index == -1) {
                throw new InvalidUserDataException("Taskpath " + taskPath + " is not a valid path.");
            }
            this.projectPath = index == 0 ? Project.PATH_SEPARATOR : taskPath.substring(0, index);
            this.taskName = taskPath.substring(index + 1);
        }

        public String getProjectPath() {
            return projectPath;
        }

        public String getTaskName() {
            return taskName;
        }

        public String toString() {
            return projectPath + Project.PATH_SEPARATOR + taskName;
        }
    }

}