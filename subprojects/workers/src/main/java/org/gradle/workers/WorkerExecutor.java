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
import org.gradle.api.Incubating;

/**
 * Worker Executor.
 *
 * @since 3.5
 */
@Incubating
public interface WorkerExecutor {
    /**
     * Submits a piece of work to be executed.
     *
     * Execution of the work may begin immediately.
     *
     * Forked work will execute in an idle daemon that meets the requirements set on this builder.  If no
     * idle daemons are available, a new daemon will be started.  Any errors will be thrown from  {@link #await()}.
     *
     * In the event that an error is thrown while submitting work, all uncompleted work will be canceled.
     */
    void submit(Class<? extends Runnable> actionClass, Action<? super WorkerConfiguration> configAction);

    /**
     * Blocks until all work associated with the current build operation is complete.
     *
     * @throws WorkerExecutionException when a failure occurs while executing the work.
     */
    void await() throws WorkerExecutionException;
}
