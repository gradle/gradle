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

import groovy.lang.Closure;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author Hans Dockter
 */
public class DefaultTaskExecuter implements TaskExecuter {
    private static Logger logger = LoggerFactory.getLogger(DefaultTaskExecuter.class);

    private Dag<Task> dag;
    private boolean populated;
    private List<TaskExecutionGraphListener> listeners = new ArrayList<TaskExecutionGraphListener>();

    public DefaultTaskExecuter(Dag<Task> dag) {
        this.dag = dag;
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        Clock clock = new Clock();
        fillDag(tasks);
        populated = true;
        logger.debug("Timing: Creating the DAG took " + clock.getTime());

    }

    public boolean execute() {
        Clock clock = new Clock();

        for (TaskExecutionGraphListener listener : listeners) {
            listener.graphPopulated(this);
        }

        try {
            boolean dagNeutral = execute(new TreeSet<Task>(dag.getSources()));
            logger.debug("Timing: Executing the DAG took " + clock.getTime());
            return !dagNeutral;
        } finally {
            dag.reset();
        }
    }

    public boolean execute(Iterable<? extends Task> tasks) {
        addTasks(tasks);
        return execute();
    }

    private void fillDag(Iterable<? extends Task> tasks) {
        for (Task task : tasks) {
            logger.debug("Find dependsOn tasks for {}", task);
            Set<? extends Task> dependsOnTasks = task.getTaskDependencies().getDependencies(task);
            addTask(task, dependsOnTasks);
            if (dependsOnTasks.size() > 0) {
                logger.debug("Found dependsOn tasks for {}: {}", task, dependsOnTasks);
                fillDag(dependsOnTasks);
            } else {
                logger.debug("Found no dependsOn tasks for {}", task);
            }
        }
    }

    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        listeners.add(listener);
    }

    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        listeners.remove(listener);
    }

    public void whenReady(final Closure closure) {
        listeners.add(new TaskExecutionGraphListener() {
            public void graphPopulated(TaskExecutionGraph graph) {
                closure.call(graph);
            }
        });
    }

    private void addTask(Task task, Iterable<? extends Task> dependsOnTasks) {
        logger.debug("Add task: {} DependsOnTasks: {}", task, dependsOnTasks);
        dag.addVertex(task);
        for (Task dependsOnTask : dependsOnTasks) {
            if (!dag.addEdge(task, dependsOnTask)) {
                throw new CircularReferenceException(String.format("Can't establish dependency %s ==> %s", task, dependsOnTask));
            }
        }
    }

    private boolean execute(Set<? extends Task> tasks) {
        boolean dagNeutral = true;
        for (Task task : tasks) {
            dagNeutral = execute(new TreeSet<Task>(dag.getChildren(task)));
            if (!task.getExecuted()) {
                logger.info("Executing: " + task);
                ((TaskInternal) task).execute();
                if (dagNeutral) {
                    dagNeutral = task.isDagNeutral();
                }
            }
        }
        return dagNeutral;
    }

    public boolean hasTask(String path) {
        assertPopulated();
        assert path != null && path.length() > 0;
        for (Task task : getAllTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public List<Task> getAllTasks() {
        assertPopulated();
        Set<Task> tasks = new LinkedHashSet<Task>();
        accumulateTasks(new TreeSet<Task>(dag.getSources()), tasks);
        return new ArrayList<Task>(tasks);
    }

    private void assertPopulated() {
        if (!populated) {
            throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
        }
    }

    private void accumulateTasks(Set<? extends Task> tasks, Collection<Task> taskList) {
        for (Task task : tasks) {
            accumulateTasks(new TreeSet<Task>(dag.getChildren(task)), taskList);
            taskList.add(task);
        }
    }
    
    public Set<Project> getProjects() {
        assertPopulated();
        HashSet<Project> projects = new HashSet<Project>();
        for (Task task : getAllTasks()) {
            projects.add(task.getProject());
        }
        return projects;
    }

}
