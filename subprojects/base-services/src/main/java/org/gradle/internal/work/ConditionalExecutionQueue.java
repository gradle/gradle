/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.internal.concurrent.Stoppable;

/**
 * Represents a queue of executions that can run when a provided resource lock can be acquired.  The typical use case would
 * be that a worker lease must be acquired before execution.
 */
public interface ConditionalExecutionQueue<T> extends Stoppable {
    /**
     * Submit a new conditional execution to the queue.  The execution will occur asynchronously when the provided
     * resource lock (see {@link ConditionalExecution#getResourceLock()}) can be acquired.  On completion,
     * {@link ConditionalExecution#complete()} will be called.
     */
    void submit(ConditionalExecution<T> execution);

    /**
     * Expand the execution queue worker pool.  This should be called before an execution in the queue is blocked waiting
     * on another execution (e.g. work that submits and waits on other work).
     */
    void expand();
}
