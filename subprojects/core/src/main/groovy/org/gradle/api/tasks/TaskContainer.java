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
import org.gradle.internal.HasInternalProtocol;

import java.util.Map;

/**
 * <p>A {@code TaskContainer} is responsible for managing a set of {@link Task} instances.</p>
 *
 * <p>You can obtain a {@code TaskContainer} instance by calling {@link org.gradle.api.Project#getTasks()}, or using the
 * {@code tasks} property in your build script.</p>
 */
@HasInternalProtocol
public interface TaskContainer extends TaskCollection<Task>, PolymorphicDomainObjectContainer<Task> {
    /**
     * <p>Locates a task by path. You can supply a task name, a relative path, or an absolute path. Relative paths are
     * interpreted relative to the project for this container. This method returns null if no task with the given path
     * exists.</p>
     *
     * @param path the path of the task to be returned
     * @return The task. Returns null if so such task exists.
     */
    Task findByPath(String path);

    /**
     * <p>Locates a task by path. You can supply a task name, a relative path, or an absolute path. Relative paths are
     * interpreted relative to the project for this container. This method throws an exception if no task with the given
     * path exists.</p>
     *
     * @param path the path of the task to be returned
     * @return The task. Never returns null
     * @throws UnknownTaskException If no task with the given path exists.
     */
    Task getByPath(String path) throws UnknownTaskException;

    /**
     * <p>Creates a {@link Task} and adds it to this container. A map of creation options can be passed to this method
     * to control how the task is created. The following options are available:</p>
     *
     * <table>
     *
     * <tr><th>Option</th><th>Description</th><th>Default Value</th></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_NAME}</code></td><td>The name of the task to create.</td><td>None.
     * Must be specified.</td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_TYPE}</code></td><td>The class of the task to
     * create.</td><td>{@link org.gradle.api.DefaultTask}</td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_ACTION}</code></td><td>The closure or {@link Action} to
     * execute when the task executes. See {@link Task#doFirst(Action)}.</td><td><code>null</code></td></tr>
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
     * <p>If a task with the given name already exists in this container and the <code>{@value org.gradle.api.Task#TASK_OVERWRITE}</code>
     * option is not set to true, an exception is thrown.</p>
     *
     * @param options The task creation options.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    Task create(Map<String, ?> options) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} adds it to this container. A map of creation options can be passed to this method to
     * control how the task is created. See {@link #create(java.util.Map)} for the list of options available. The given
     * closure is used to configure the task before it is returned by this method.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param options The task creation options.
     * @param configureClosure The closure to use to configure the task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    Task create(Map<String, ?> options, Closure configureClosure) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name adds it to this container. The given closure is used to configure
     * the task before it is returned by this method.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @param configureClosure The closure to use to configure the task.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    Task create(String name, Closure configureClosure) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    Task create(String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and type, and adds it to this container.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created.
     * @param type The type of task to create.
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    <T extends Task> T create(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and type, configures it with the given action, and adds it to this container.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created.
     * @param type The type of task to create.
     * @param configuration The action to configure the task with.
     * @return The newly created task object.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container, replacing any existing task with the
     * same name.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
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
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     */
    <T extends Task> T replace(String name, Class<T> type);
}
