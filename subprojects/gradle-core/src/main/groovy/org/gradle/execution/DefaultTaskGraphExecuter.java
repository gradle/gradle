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

package org.gradle.execution;

import groovy.lang.Closure;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultTaskGraphExecuter implements TaskGraphExecuter {
    private static Logger logger = LoggerFactory.getLogger(DefaultTaskGraphExecuter.class);

    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final Set<Task> executionPlan = new LinkedHashSet<Task>();
    private boolean populated;
    private Spec<? super Task> filter = Specs.satisfyAll();

    public DefaultTaskGraphExecuter(ListenerManager listenerManager) {
        graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        Clock clock = new Clock();

        Set<Task> sortedTasks = new TreeSet<Task>();
        for (Task task : tasks) {
            sortedTasks.add(task);
        }
        fillDag(sortedTasks);
        populated = true;

        logger.debug("Timing: Creating the DAG took " + clock.getTime());
    }

    public void execute() {
        Clock clock = new Clock();

        graphListeners.getSource().graphPopulated(this);

        try {
            doExecute(executionPlan);
            logger.debug("Timing: Executing the DAG took " + clock.getTime());
        } finally {
            executionPlan.clear();
        }
    }

    public void execute(Iterable<? extends Task> tasks) {
        addTasks(tasks);
        execute();
    }

    private void fillDag(Collection<? extends Task> tasks) {
        Set<Task> visiting = new HashSet<Task>();
        List<Task> queue = new ArrayList<Task>();
        queue.addAll(tasks);
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            Task task = queue.get(0);
            if (!filter.isSatisfiedBy(task)) {
                // Filtered - skip
                queue.remove(0);
                continue;
            }
            if (executionPlan.contains(task)) {
                // Already in plan - skip
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<Task> dependsOnTasks = new TreeSet<Task>(Collections.reverseOrder());
                dependsOnTasks.addAll(context.getDependencies(task));
                for (Task dependsOnTask : dependsOnTasks) {
                    if (visiting.contains(dependsOnTask)) {
                        throw new CircularReferenceException(String.format(
                                "Circular dependency between tasks. Cycle includes [%s, %s].", task, dependsOnTask));
                    }
                    queue.add(0, dependsOnTask);
                }
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                queue.remove(0);
                visiting.remove(task);
                executionPlan.add(task);
            }
        }
    }

    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listener);
    }

    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    public void whenReady(final Closure closure) {
        graphListeners.add("graphPopulated", closure);
    }

    public void addTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.add(listener);
    }

    public void removeTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    public void beforeTask(final Closure closure) {
        taskListeners.add("beforeExecute", closure);
    }

    public void afterTask(final Closure closure) {
        taskListeners.add("afterExecute", closure);
    }

    private void doExecute(Iterable<? extends Task> tasks) {
        for (Task task : tasks) {
            executeTask(task);
        }
    }

    private void executeTask(Task task) {
        taskListeners.getSource().beforeExecute(task);
        try {
            ((TaskInternal) task).execute();
        } finally {
            taskListeners.getSource().afterExecute(task, task.getState());
        }
    }

    public boolean hasTask(Task task) {
        assertPopulated();
        return executionPlan.contains(task);
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
        return new ArrayList<Task>(executionPlan);
    }

    private void assertPopulated() {
        if (!populated) {
            throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
        }
    }
}
