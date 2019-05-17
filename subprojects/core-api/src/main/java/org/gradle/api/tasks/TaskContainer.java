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
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.provider.Provider;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;
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
    @Nullable
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
     * <tr><td><code>{@value org.gradle.api.Task#TASK_GROUP}</code></td><td>The group of the task.</td><td><code>null
     * </code></td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_DESCRIPTION}</code></td><td>The description of the task.</td><td>
     * <code>null</code></td></tr>
     *
     * <tr><td><code>{@value org.gradle.api.Task#TASK_CONSTRUCTOR_ARGS}</code></td><td>The arguments to pass to the task class constructor.</td><td>
     * <code>null</code></td></tr>
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
     * @throws NullPointerException If any of the values in <code>{@value org.gradle.api.Task#TASK_CONSTRUCTOR_ARGS}</code> is null.
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
    @Override
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
    @Override
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
    @Override
    <T extends Task> T create(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and type, passing the given arguments to the {@code @Inject}-annotated constructor,
     * and adds it to this container.  All values passed to the task constructor must be non-null; otherwise a
     * {@code NullPointerException} will be thrown</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created.
     * @param type The type of task to create.
     * @param constructorArgs The arguments to pass to the task constructor
     * @return The newly created task object
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @throws NullPointerException If any of the values in {@code constructorArgs} is null.
     * @since 4.7
     */
    @Incubating
    <T extends Task> T create(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException;

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
    @Override
    <T extends Task> T create(String name, Class<T> type, Action<? super T> configuration) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created and configured when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(java.lang.String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link NamedDomainObjectContainer#create(java.lang.String, org.gradle.api.Action)} or {@link #create(java.lang.String)}, as those methods will eagerly create and configure the task, regardless of whether that task is required for the current build or not. This method, on the other hand, will defer creation and configuration until required.</p>
     *
     * @param name The name of the task.
     * @param configurationAction The action to run to configure the task. This action runs when the task is required.
     * @return A {@link Provider} that whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 4.9
     */
    @Override
    TaskProvider<Task> register(String name, Action<? super Task> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created and configured when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(java.lang.String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(java.lang.String, java.lang.Class, org.gradle.api.Action)} or {@link #create(java.lang.String, java.lang.Class)}, as those methods will eagerly create and configure the task, regardless of whether that task is required for the current build or not. This method, on the other hand, will defer creation and configuration until required.</p>
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param configurationAction The action to run to configure the task. This action runs when the task is required.
     * @param <T> The task type
     * @return A {@link Provider} that whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 4.9
     */
    @Override
    <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(java.lang.String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(java.lang.String, java.lang.Class, org.gradle.api.Action)} or {@link #create(java.lang.String, java.lang.Class)}, as those methods will eagerly create and configure the task, regardless of whether that task is required for the current build or not. This method, on the other hand, will defer creation until required.</p>
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param <T> The task type
     * @return A {@link Provider} that whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 4.9
     */
    @Override
    <T extends Task> TaskProvider<T> register(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required passing the given arguments to the {@code @Inject}-annotated constructor. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(java.lang.String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method. All values passed to the task constructor must be non-null; otherwise a {@code NullPointerException} will be thrown
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(java.lang.String, java.lang.Class, org.gradle.api.Action)} or {@link #create(java.lang.String, java.lang.Class)}, as those methods will eagerly create and configure the task, regardless of whether that task is required for the current build or not. This method, on the other hand, will defer creation until required.</p>
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param constructorArgs The arguments to pass to the task constructor
     * @param <T> The task type
     * @return A {@link Provider} that whose value will be the task, when queried.
     * @throws NullPointerException If any of the values in {@code constructorArgs} is null.
     * @since 4.9
     */
    <T extends Task> TaskProvider<T> register(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(java.lang.String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(java.lang.String)}, as that method will eagerly create the task, regardless of whether that task is required for the current build or not. This method, on the other hand, will defer creation until required.</p>
     *
     * @param name The name of the task.
     * @return A {@link Provider} that whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 4.9
     */
    @Override
    TaskProvider<Task> register(String name) throws InvalidUserDataException;

    /**
     * <p>Creates a {@link Task} with the given name and adds it to this container, replacing any existing task with the
     * same name.</p>
     *
     * <p>After the task is added, it is made available as a property of the project, so that you can reference the task
     * by name in your build file. See <a href="../Project.html#properties">here</a> for more details.</p>
     *
     * @param name The name of the task to be created
     * @return The newly created task object
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
     */
    <T extends Task> T replace(String name, Class<T> type);
}
