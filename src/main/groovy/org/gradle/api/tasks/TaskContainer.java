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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * <p>A {@code TaskContainer} is responsible for managing a set of {@link Task} instances.</p>
 *
 * <p>You can obtain a {@code TaskContainer} instance by calling {@link org.gradle.api.Project#getTasks()}.</p>
 */
public interface TaskContainer extends Iterable<Task> {
    /**
     * Returns the tasks in this container.
     *
     * @return The tasks. Returns an empty set if this container is empty.
     */
    Set<Task> getAll();

    /**
     * Returns the tasks in this container, as a map from task name to {@code Task} instance.
     *
     * @return The tasks. Returns an empty map if this container is empty.
     */
    Map<String, Task> getAsMap();

    /**
     * Returns the tasks in this container which meet the given criteria.
     *
     * @param spec The criteria to use.
     * @return The matching tasks. Returns an empty set if there are no such tasks in this container.
     */
    Set<Task> get(Spec<? super Task> spec);

    /**
     * Locates a task by name, returning null if there is no such task.
     *
     * @param name The task name
     * @return The task with the given name, or null if there is no such task in this container.
     */
    Task find(String name);

    /**
     * Locates a task by name, failing if there is no such task. The given task closure is executed against the task
     * before it is returned from this method.
     *
     * @param name The task name
     * @param configureClosure The closure to use to configure the task.
     * @return The task with the given name. Never returns null.
     * @throws UnknownTaskException when there is no such task in this container.
     */
    Task get(String name, Closure configureClosure) throws UnknownTaskException;

    /**
     * Locates a task by name, failing if there is no such task. You can call this method in your build script by using
     * the {@code .} operator:
     *
     * <pre>
     * tasks.someTask.dependsOn 'another-task'
     * </pre>
     *
     * @param name The task name
     * @return The task with the given name. Never returns null.
     * @throws UnknownTaskException when there is no such task in this container.
     */
    Task get(String name) throws UnknownTaskException;

    /**
     * Adds a {@code TaskAction} to be performed when a task is added to this container.
     *
     * @param action The action to be performed
     * @return the supplied action
     */
    Action<? super Task> whenTaskAdded(Action<? super Task> action);

    /**
     * Adds a {@code TaskAction} to be performed when a task of the given type is added to this container.
     *
     * @param action The action to be performed
     * @param type The type of tasks to be notified of
     * @return the supplied action
     */
    <T extends Task> Action<T> whenTaskAdded(Class<T> type, Action<T> action);

    /**
     * Adds a closure to be called when a task is added to this container. The task is passed to the closure as the
     * parameter.
     *
     * @param closure The closure to be called
     */
    void whenTaskAdded(Closure closure);

    /**
     * Locates a task by name, failing if there is no such task. This method is identical to {@link #get(String)}. You
     * can call this method in your build script by using the groovy {@code []} operator:
     *
     * <pre>
     * tasks['some-task'].dependsOn 'another-task'
     * </pre>
     *
     * @param name The task name
     * @return The tasl with the given name. Never returns null.
     * @throws UnknownTaskException when there is no such task in this container.
     */
    Task getAt(String name) throws UnknownTaskException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container. Before the task is returned, the
     * given action closure is passed to the task's {@link Task#doFirst(TaskAction)} method. A map of creation options
     * can be passed to this method to control how the task is created. The following options are available:</p>
     *
     * <table>
     *
     * <tr><th>Option</th><th>Description</th><th>Default Value</th></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_TYPE}</code></td><td>The class of the task to
     * create.</td><td>{@link org.gradle.api.internal.DefaultTask}</td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_OVERWRITE}</code></td><td>Replace an existing
     * task?</td><td><code>false</code></td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_DEPENDS_ON}</code></td><td>The dependencies of the task. See <a
     * href="../Task.html#dependencies">here</a> for more details.</td><td><code>[]</code></td></tr>
     *
     * </table>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file.  See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * <p>If a task with the given name already exists in this container and the <code>override</code> option is not set
     * to true, an exception is thrown.</p>
     *
     * @param options The task creation options.
     * @param name The name of the task to be created
     * @param taskAction The closure to be passed to the {@link Task#doFirst(TaskAction)} method of the created task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task add(Map<String, ?> options, String name, TaskAction taskAction) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task add(String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and type, and adds it to this container.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created.
     * @param type The type of task to create.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    <T extends Task> T add(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container, replacing any existing task with the
     * same name.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    Task replace(String name);

    /**
     * <p>Creates a {@link Task} with the given name and type, and adds it to this container, replacing any existing
     * task of the same name.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created.
     * @param type The type of task to create.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exsists in this project.
     */
    <T extends Task> T replace(String name, Class<T> type);

    /**
     * Adds a rule to this container. The given rule is invoked when an unknown task is requested.
     *
     * @param rule The rule to add.
     * @return The added rule.
     */
    Rule addRule(Rule rule);

    /**
     * Returns the rules used by this container.
     *
     * @return The rules, in the order they will be applied.
     */
    List<Rule> getRules();
}
