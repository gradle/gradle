/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.process.internal.worker.request;

import org.gradle.process.internal.worker.child.WorkerLoggingProtocol;
import org.gradle.process.internal.worker.problem.WorkerProblemProtocol;

/**
 * Protocol for sending information back from a worker process to the daemon.
 * <p>
 * This protocol is extended with the {@link WorkerLoggingProtocol} (for sending log messages)
 * and {@link WorkerProblemProtocol} (for sending {@link org.gradle.api.problems.Problem}s through).
 * <p>
 * Bundling these protocols together will use a single connection for all communication,
 * which means that messages are guaranteed to be delivered <b>in-order</b> (i.e. log and problem messages are delivered before the completion/failed message).
 */
public interface ResponseProtocol extends WorkerLoggingProtocol, WorkerProblemProtocol {
    /**
     Called when the method completes successfully
     */
    void completed(Object result);

    /**
     Called when the method throws an exception
     */
    void failed(Throwable failure);

    /**
     * Called when some other problem occurs
     */
    void infrastructureFailed(Throwable failure);
}
