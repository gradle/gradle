/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers;

import org.gradle.api.Action;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Allows work to be submitted for asynchronous execution.  This api allows for safe, concurrent execution of work items and enables:
 *
 * <ul>
 *     <li>Parallel execution of work items within a single task</li>
 *     <li>Execution in isolated contexts such as an isolated classloader or even a separate process</li>
 *     <li>Safe execution of multiple tasks in parallel</li>
 * </ul>
 *
 * <p>Work should be submitted with a {@link WorkAction} class representing the implementation of the unit of work
 * and an action to configure the parameters of the unit of work (via {@link WorkParameters}).
 *
 * <pre>
 *      workerExecutor.noIsolation().submit(MyWorkActionImpl.class) { MyWorkParameters parameters -&gt;
 *          parameters.inputFile = project.file('foo')
 *          parameters.outputFile = project.layout.buildDirectory.file('bar')
 *      }
 * </pre>
 *
 * <p>
 * An instance of the executor can be injected into a task by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 *
 * @since 3.5
 */
@ServiceScope(Scopes.Project.class)
public interface WorkerExecutor {
    /**
     * Submits a piece of work to be executed asynchronously.
     *
     * Execution of the work may begin immediately.
     *
     * Work configured with {@link IsolationMode#PROCESS} will execute in an idle daemon that meets the requirements set
     * in the {@link WorkerConfiguration}.  If no idle daemons are available, a new daemon will be started.  Any errors
     * will be thrown from {@link #await()} or from the surrounding task action if {@link #await()} is not used.
     */
    @Deprecated
    void submit(Class<? extends Runnable> actionClass, Action<? super WorkerConfiguration> configAction);

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution with no isolation.
     *
     * @since 5.6
     */
    WorkQueue noIsolation();

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution with an isolated classloader.
     *
     * @since 5.6
     */
    WorkQueue classLoaderIsolation();

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution in a daemon process.
     *
     * Work will execute in an idle daemon, if available.  If no idle daemons are available, a new daemon will be started.
     *
     * @since 5.6
     */
    WorkQueue processIsolation();

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution with no isolation and the requirements specified in the supplied {@link WorkerSpec}.
     *
     * @since 5.6
     */
    WorkQueue noIsolation(Action<? super WorkerSpec> action);

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution with an isolated classloader and the requirements specified in the supplied {@link ClassLoaderWorkerSpec}.
     *
     * @since 5.6
     */
    WorkQueue classLoaderIsolation(Action<? super ClassLoaderWorkerSpec> action);

    /**
     * Creates a {@link WorkQueue} to submit work for asynchronous execution in a daemon process.
     *
     * Work will execute in an idle daemon matching the requirements specified in the supplied {@link ProcessWorkerSpec}, if available.  If no idle daemons are available, a new daemon will be started.
     *
     * @since 5.6
     */
    WorkQueue processIsolation(Action<? super ProcessWorkerSpec> action);

    /**
     * Blocks until all work associated with the current build operation is complete.  Note that when using this method inside
     * a task action, it will block completion of the task action until all submitted work is complete.  This means that other
     * tasks from the same project cannot be run in parallel while the task action is still executing.
     *
     * @throws WorkerExecutionException when a failure occurs while executing the work.
     */
    void await() throws WorkerExecutionException;
}
