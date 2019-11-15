/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.Incubating;

/**
 * Represents a queue of work items with a uniform set of worker requirements.
 *
 * Note that this object is not thread-safe.
 *
 * @since 5.6
 */
@Incubating
public interface WorkQueue {
    /**
     * Submits a piece of work to be executed asynchronously.
     *
     * Execution of the work may begin immediately.
     *
     * Work submitted using {@link WorkerExecutor#processIsolation()} will execute in an idle daemon that meets the requirements set
     * in the {@link ProcessWorkerSpec}.  If no idle daemons are available, a new daemon will be started.  Any errors
     * will be thrown from {@link #await()} or from the surrounding task action if {@link #await()} is not used.
     */
    <T extends WorkParameters> void submit(Class<? extends WorkAction<T>> workActionClass, Action<? super T> parameterAction);

    /**
     * Blocks until all work associated with this queue is complete.  Note that when using this method inside
     * a task action, it will block completion of the task action until the submitted work is complete.  This means that other
     * tasks from the same project cannot be run in parallel while the task action is still executing.
     *
     * @throws WorkerExecutionException when a failure occurs while executing the work.
     */
    void await() throws WorkerExecutionException;
}
