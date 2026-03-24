/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.registration;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;

/**
 * An interface for operations relating to registering new tasks in the build.
 *
 * <p>An instance of this type can be injected into an object by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 *
 * @since 9.5.0
 */
@Incubating
public interface TaskRegistrar extends PolymorphicDomainObjectRegistrar<Task> {
    /**
     * Defines a new task, which will be created and configured when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * @param name The name of the task.
     * @param configurationAction The action to run to configure the task. This action runs when the task is required.
     * @return A {@link Provider} whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 9.5.0
     */
    @Override
    TaskProvider<Task> register(String name, Action<? super Task> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created and configured when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param configurationAction The action to run to configure the task. This action runs when the task is required.
     * @param <T> The task type
     * @return A {@link Provider} whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 9.5.0
     */
    @Override
    <T extends Task> TaskProvider<T> register(String name, Class<T> type, Action<? super T> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param <T> The task type
     * @return A {@link Provider} whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 9.5.0
     */
    @Override
    <T extends Task> TaskProvider<T> register(String name, Class<T> type) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required passing the given arguments to the {@code @Inject}-annotated constructor. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method. All values passed to the task constructor must be non-null; otherwise a {@code NullPointerException} will be thrown
     *
     * @param name The name of the task.
     * @param type The task type.
     * @param constructorArgs The arguments to pass to the task constructor
     * @param <T> The task type
     * @return A {@link Provider} whose value will be the task, when queried.
     * @throws NullPointerException If any of the values in {@code constructorArgs} is null.
     * @since 9.5.0
     */
    <T extends Task> TaskProvider<T> register(String name, Class<T> type, Object... constructorArgs) throws InvalidUserDataException;

    /**
     * Defines a new task, which will be created when it is required. A task is 'required' when the task is located using query methods such as {@link TaskCollection#getByName(String)}, when the task is added to the task graph for execution or when {@link Provider#get()} is called on the return value of this method.
     *
     * @param name The name of the task.
     * @return A {@link Provider} whose value will be the task, when queried.
     * @throws InvalidUserDataException If a task with the given name already exists in this project.
     * @since 9.5.0
     */
    @Override
    TaskProvider<Task> register(String name) throws InvalidUserDataException;
}
