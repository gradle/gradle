/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.execution;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Task;

import java.util.List;
import java.util.Set;

/**
 * <p>A <code>TaskExecutionGraph</code> is responsible for managing the execution of the {@link Task} instances which
 * are part of the build. The <code>TaskExecutionGraph</code> maintains an execution plan of tasks to be executed (or
 * which have been executed), and you can query this plan from your build file.</p>
 *
 * <p>You can access the {@code TaskExecutionGraph} by calling {@link org.gradle.api.invocation.Gradle#getTaskGraph()}.
 * In your build file you can use {@code gradle.taskGraph} to access it.</p>
 *
 * <p>The <code>TaskExecutionGraph</code> is populated only after all the projects in the build have been evaluated. It
 * is empty before then. You can receive a notification when the graph is populated, using {@link
 * #whenReady(groovy.lang.Closure)} or {@link #addTaskExecutionGraphListener(TaskExecutionGraphListener)}.</p>
 */
public interface TaskExecutionGraph {
    /**
     * <p>Adds a listener to this graph, to be notified when this graph is ready.</p>
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     */
    void addTaskExecutionGraphListener(TaskExecutionGraphListener listener);

    /**
     * <p>Remove a listener from this graph.</p>
     *
     * @param listener The listener to remove. Does nothing if this listener was never added to this graph.
     */
    void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener);

    /**
     * <p>Adds a listener to this graph, to be notified as tasks are executed.</p>
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     */
    void addTaskExecutionListener(TaskExecutionListener listener);

    /**
     * <p>Remove a listener from this graph.</p>
     *
     * @param listener The listener to remove. Does nothing if this listener was never added to this graph.
     */
    void removeTaskExecutionListener(TaskExecutionListener listener);

    /**
     * <p>Adds a closure to be called when this graph has been populated. This graph is passed to the closure as a
     * parameter.</p>
     *
     * @param closure The closure to execute when this graph has been populated.
     */
    void whenReady(Closure closure);

    /**
     * <p>Adds an action to be called when this graph has been populated. This graph is passed to the action as a
     * parameter.</p>
     *
     * @param action The action to execute when this graph has been populated.
     *
     * @since 3.1
     */
    void whenReady(Action<TaskExecutionGraph> action);

    /**
     * <p>Adds a closure to be called immediately before a task is executed. The task is passed to the closure as a
     * parameter.</p>
     *
     * @param closure The closure to execute when a task is about to be executed.
     */
    void beforeTask(Closure closure);

    /**
     * <p>Adds an action to be called immediately before a task is executed. The task is passed to the action as a
     * parameter.</p>
     *
     * @param action The action to execute when a task is about to be executed.
     *
     * @since 3.1
     */
    void beforeTask(Action<Task> action);

    /**
     * <p>Adds a closure to be called immediately after a task has executed. The task is passed to the closure as the
     * first parameter. A {@link org.gradle.api.tasks.TaskState} is passed as the second parameter. Both parameters are
     * optional.</p>
     *
     * @param closure The closure to execute when a task has been executed
     */
    void afterTask(Closure closure);

    /**
     * <p>Adds an action to be called immediately after a task has executed. The task is passed to the action as the
     * first parameter.</p>
     *
     * @param action The action to execute when a task has been executed
     *
     * @since 3.1
     */
    void afterTask(Action<Task> action);

    /**
     * <p>Determines whether the given task is included in the execution plan.</p>
     *
     * @param path the <em>absolute</em> path of the task.
     * @return true if a task with the given path is included in the execution plan.
     * @throws IllegalStateException When this graph has not been populated.
     */
    boolean hasTask(String path);

    /**
     * <p>Determines whether the given task is included in the execution plan.</p>
     *
     * @param task the task
     * @return true if the given task is included in the execution plan.
     * @throws IllegalStateException When this graph has not been populated.
     */
    boolean hasTask(Task task);

    /**
     * <p>Returns the tasks which are included in the execution plan. The tasks are returned in the order that they will
     * be executed.</p>
     *
     * @return The tasks. Returns an empty set if no tasks are to be executed.
     * @throws IllegalStateException When this graph has not been populated.
     */
    List<Task> getAllTasks();

    /**
     * <p>Returns the dependencies of a task which are part of the execution graph.</p>
     *
     * @return The tasks. Returns an empty set if there are no dependent tasks.
     * @throws IllegalStateException When this graph has not been populated or the task is not part of it.
     *
     * @since 4.6
     */
    Set<Task> getDependencies(Task task);
}
