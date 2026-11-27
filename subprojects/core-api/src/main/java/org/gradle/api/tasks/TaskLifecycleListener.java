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
 * <p>Listeners are registered by type, using {@link Task#addLifecycleListener(Class)}, {@link Task#addLifecycleListener(Task, Class)}
 * or {@link TaskCollection#addLifecycleListener(Class)}. Gradle creates a new instance of the listener
 * for every execution of a task it is registered with. Both methods of a single task execution are called
 * on the same instance, so instance fields can be used to carry state from {@link #beforeExecution(Task)}
 * to {@link #afterExecution(Task, TaskState)}.</p>
 *
 * <p>When several listeners are registered with a task, {@link #beforeExecution(Task)} is called in the order
 * the listeners were registered, and {@link #afterExecution(Task, TaskState)} is called in the reverse order.
 * The listener registered first is notified first before the execution and last after it.
 * {@code afterExecution} is called on every listener whose {@code beforeExecution} completed, and only on those.</p>
 *
 * <p>Implementations can be abstract classes or interfaces. They can receive shared build services via
 * {@link org.gradle.api.services.ServiceReference @ServiceReference}. A build service referenced this way is used
 * by the task the listener is registered with, as if the task referenced it itself.
 * Shared state between task executions must be kept in a build service, not in the listener itself.</p>
 *
 * <p>Both methods are called synchronously as part of the task execution. Listeners of tasks executing in
 * parallel are notified concurrently. Each listener instance is used by a single task execution, so its fields
 * need no synchronization, but build services and other state shared between task executions must be thread-safe.</p>
 *
 * <p>Listeners are also notified for tasks that are up-to-date, loaded from the build cache or skipped, so their cost
 * is paid on every build, even when no task does any work. Work that only reports the outcome of tasks, such as
 * uploading results, is better done by a listener registered with
 * {@link org.gradle.build.event.BuildEventsListenerRegistry#onTaskCompletion(org.gradle.api.provider.Provider)},
 * which is notified asynchronously.</p>
 *
 * <p>Unlike tasks, listeners cannot have {@link Nested @Nested} properties.
 * Build services must be referenced via {@code @ServiceReference} properties of the listener itself.</p>
 *
 * <p>The following services can be injected via {@code @Inject}:</p>
 * <ul>
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
     * <p>Called for every task that Gradle tries to execute, including tasks that are later skipped, up-to-date or loaded from the build cache.</p>
     *
     * <p>This method is not meant for configuring the task. If it changes the value of a task input anyway,
     * the changed value is fingerprinted, but no task dependencies are added for it, as the task graph is already built.
     * The outputs of the task are already resolved and must not be changed. This includes values that
     * determine which outputs the task has, such as whether a report is enabled.</p>
     *
     * <p>Throwing an exception fails the task. The task actions are not executed in this case.
     * The listeners registered after this one are not notified at all, and {@link #afterExecution(Task, TaskState)}
     * is not called on this listener. It is still called on the listeners registered before this one.</p>
     *
     * @param task the task about to be executed
     * @since 9.10.0
     */
    default void beforeExecution(T task) {}

    /**
     * Called after the task has been executed, regardless of its outcome.
     * This includes a task that was not executed because another listener failed in {@link #beforeExecution(Task)}.
     *
     * <p>The outputs of the task have already been recorded at this point.
     * Throwing an exception fails the task in this build, but does not affect up-to-date checks or the build cache in later builds.
     * The other listeners are still notified, and see this failure in the task state.</p>
     *
     * @param task the executed task
     * @param state the state of the task, including the failure, if any
     * @since 9.10.0
     */
    default void afterExecution(T task, TaskState state) {}
}
