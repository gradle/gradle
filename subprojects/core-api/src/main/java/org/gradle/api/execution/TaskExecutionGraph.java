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
package org.gradle.api.execution;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;
import java.util.Set;

// Public `TaskExecutionGraph` service shadowed at the project scope by the IP reporting wrapper
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
 * @since 0.7
 */
@ServiceScope({Scope.Build.class, Scope.Project.class})
public interface TaskExecutionGraph {
    /**
     * <p>Adds a listener to this graph, to be notified when this graph is ready.</p>
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     * @since 0.7
     */
    void addTaskExecutionGraphListener(TaskExecutionGraphListener listener);

    /**
     * <p>Remove a listener from this graph.</p>
     *
     * @param listener The listener to remove. Does nothing if this listener was never added to this graph.
     * @since 0.7
     */
    void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener);

    /**
     * <p>Adds a listener to this graph, to be notified as tasks are executed.</p>
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     * @deprecated This method is not supported when configuration caching is enabled.
     * @since 0.7
     */
    @Deprecated
    void addTaskExecutionListener(TaskExecutionListener listener);

    /**
     * <p>Remove a listener from this graph.</p>
     *
     * @param listener The listener to remove. Does nothing if this listener was never added to this graph.
     * @deprecated This method is not supported when configuration caching is enabled.
     * @since 0.7
     */
    @Deprecated
    void removeTaskExecutionListener(TaskExecutionListener listener);

    /**
     * <p>Adds a closure to be called when this graph has been populated. This graph is passed to the closure as a
     * parameter.</p>
     *
     * <p>When the configuration cache is enabled, this callback only
     * fires during the configuration phase (a cache miss). On a cache hit,
     * the task graph is loaded from the cache and this callback is not
     * invoked.
     *
     * @param closure The closure to execute when this graph has been populated.
     * @since 0.7
     */
    void whenReady(Closure closure);

    /**
     * <p>Adds an action to be called when this graph has been populated. This graph is passed to the action as a
     * parameter.</p>
     *
     * <p>When the configuration cache is enabled, this callback only
     * fires during the configuration phase (a cache miss). On a cache hit,
     * the task graph is loaded from the cache and this callback is not
     * invoked.
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
     * @deprecated This method is not supported when configuration caching is enabled.
     * @since 0.7
     */
    @Deprecated
    void beforeTask(Closure closure);

    /**
     * <p>Adds an action to be called immediately before a task is executed. The task is passed to the action as a
     * parameter.</p>
     *
     * @param action The action to execute when a task is about to be executed.
     * @deprecated This method is not supported when configuration caching is enabled.
     *
     * @since 3.1
     */
    @Deprecated
    void beforeTask(Action<Task> action);

    /**
     * <p>Adds a closure to be called immediately after a task has executed. The task is passed to the closure as the
     * first parameter. A {@link org.gradle.api.tasks.TaskState} is passed as the second parameter. Both parameters are
     * optional.</p>
     *
     * @param closure The closure to execute when a task has been executed
     * @deprecated This method is not supported when configuration caching is enabled.
     * @since 0.7
     */
    @Deprecated
    void afterTask(Closure closure);

    /**
     * <p>Adds an action to be called immediately after a task has executed. The task is passed to the action as the
     * first parameter.</p>
     *
     * @param action The action to execute when a task has been executed
     * @deprecated This method is not supported when configuration caching is enabled.
     *
     * @since 3.1
     */
    @Deprecated
    void afterTask(Action<Task> action);

    /**
     * <p>Determines whether the given task is included in the execution plan.</p>
     *
     * @param path the <em>absolute</em> path of the task.
     * @return true if a task with the given path is included in the execution plan.
     * @throws IllegalStateException When this graph has not been populated.
     * @since 0.7
     */
    boolean hasTask(String path);

    /**
     * <p>Determines whether the given task is included in the execution plan.</p>
     *
     * @param task the task
     * @return true if the given task is included in the execution plan.
     * @throws IllegalStateException When this graph has not been populated.
     * @since 0.7
     */
    boolean hasTask(Task task);

    /**
     * <p>Returns the tasks which are included in the execution plan.
     * The order of the tasks in the result is compatible with the constraints (dependsOn/mustRunAfter/etc) set in the build configuration.
     * However, Gradle may execute tasks in a slightly different order to speed up the overall execution while still respecting the constraints.
     * </p>
     *
     * @return The tasks. Returns an empty list if no tasks are to be executed.
     * @since 0.7
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

    /**
     * Returns a provider that tells whether the task with the given path is part of this build's task graph.
     *
     * <p>The provider can only be queried once the task graph is ready, which means during task execution,
     * for example from {@link Task#onlyIf(org.gradle.api.specs.Spec)} or as a task input.
     * Querying it during configuration fails.</p>
     *
     * <p>The configuration cache stores the provider as a question, not as an answer:
     * when the configuration is reused, the provider is answered again against the task graph of the current build.</p>
     *
     * <p>The task may belong to any project of this build.</p>
     *
     * @param taskPath the absolute path of the task, for example {@code ":app:test"}
     * @return a provider that is {@code true} if the task is part of the task graph
     * @throws IllegalArgumentException if the path is not absolute
     * @since 9.9.0
     */
    @Incubating
    Provider<Boolean> isScheduled(String taskPath);

    /**
     * Returns a provider that tells whether any task of the given type, in any project of this build, is part of this build's task graph.
     *
     * <p>Tasks are matched by the fully qualified name of the type they were registered with, including its supertypes and interfaces.
     * This means that the same task type loaded by different class loaders, for example by the same plugin applied to several projects
     * with different build script classpaths, still matches.</p>
     *
     * <p>The provider can only be queried once the task graph is ready, which means during task execution.
     * Querying it during configuration fails.</p>
     *
     * <p>The configuration cache stores the provider as a question, not as an answer:
     * when the configuration is reused, the provider is answered again against the task graph of the current build.</p>
     *
     * @param taskType the task type to look for
     * @return a provider that is {@code true} if at least one task of the given type is part of the task graph
     * @since 9.9.0
     */
    @Incubating
    Provider<Boolean> anyScheduled(Class<? extends Task> taskType);
}
