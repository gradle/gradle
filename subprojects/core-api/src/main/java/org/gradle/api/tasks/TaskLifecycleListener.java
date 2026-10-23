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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.Task;

/**
 * A listener notified before and after the execution of a single task.
 *
 * <p>Listeners are registered by type, using {@link Task#addLifecycleListener(Class)} or
 * {@link TaskCollection#addLifecycleListener(Class)}. Gradle creates a new instance of the listener
 * for every execution of a task it is registered with. Both methods of a single task execution are called
 * on the same instance, so instance fields can be used to carry state from {@link #beforeExecution(Task)}
 * to {@link #afterExecution(Task, TaskState)}.</p>
 *
 * <p>Implementations can be abstract classes or interfaces. They can receive shared build services via
 * {@link org.gradle.api.services.ServiceReference @ServiceReference}. A build service referenced this way is used
 * by the task the listener is registered with, as if the task referenced it itself.
 * Shared state between task executions must be kept in a build service, not in the listener itself.</p>
 *
 * <p>Unlike tasks, listeners cannot have {@link Nested @Nested} properties.
 * Build services must be referenced via {@code @ServiceReference} properties of the listener itself.</p>
 *
 * <p>The following services can be injected via {@code @Inject}:</p>
 * <ul>
 *     <li>{@link org.gradle.api.model.ObjectFactory}</li>
 *     <li>{@link org.gradle.api.provider.ProviderFactory}</li>
 *     <li>{@link org.gradle.api.file.FileSystemOperations}</li>
 *     <li>{@link org.gradle.api.file.ArchiveOperations}</li>
 *     <li>{@link org.gradle.process.ExecOperations}</li>
 *     <li>{@link org.gradle.api.file.ProjectLayout}</li>
 *     <li>{@link org.gradle.api.problems.Problems}</li>
 * </ul>
 *
 * <p>Listeners must not produce task outputs.</p>
 *
 * @param <T> the type of task this listener can be registered with
 * @since 9.10.0
 */
@Incubating
public interface TaskLifecycleListener<T extends Task> {

    /**
     * Called before the task is executed, before its inputs are snapshotted
     * and before up-to-date and build cache checks.
     *
     * <p>Called for every scheduled task, including tasks that are later skipped, up-to-date or loaded from the build cache.</p>
     *
     * <p>The values of task inputs can be changed here, and the changed values are fingerprinted.
     * The outputs of the task are already resolved and cannot be changed. This includes values that
     * determine which outputs the task has, such as whether a report is enabled.
     * Changing an input value does not add task dependencies, so a task producing files used by
     * the changed input must be wired at configuration time.</p>
     *
     * <p>Throwing an exception fails the task. The task actions are not executed in this case.</p>
     *
     * @param task the task about to be executed
     * @since 9.10.0
     */
    default void beforeExecution(T task) {}

    /**
     * Called after the task has been executed, regardless of its outcome.
     *
     * <p>The outputs of the task have already been recorded at this point.
     * Throwing an exception fails the task in this build, but does not affect up-to-date checks or the build cache in later builds.</p>
     *
     * @param task the executed task
     * @param state the state of the task, including the failure, if any
     * @since 9.10.0
     */
    default void afterExecution(T task, TaskState state) {}
}
