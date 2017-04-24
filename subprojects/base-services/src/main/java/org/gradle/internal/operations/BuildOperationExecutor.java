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

package org.gradle.internal.operations;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationState;

/**
 * Runs build operations. These are the pieces of work that make up a build. Build operations can be nested inside other
 * build operations.
 *
 * The executor provides several capabilities:
 *
 * <ul>
 *     <p>Fires events via {@link BuildOperationListener}. For example, this means that notification of build operation
 *     execution can be received by tooling API clients.</p>
 *     <p>Generates progress logging events.</p>
 * </ul>
 *
 * <p>Operations are executed synchronously or asynchronous.</p>
 */
@ThreadSafe
public interface BuildOperationExecutor {
    /**
     * Runs the given build operation synchronously. Invokes the given operation from the current thread.
     *
     * <p>Rethrows any exception thrown by the action.</p>
     */
    void run(RunnableBuildOperation buildOperation);

    /**
     * Calls the given build operation synchronously. Invokes the given operation from the current thread.
     * Returns the result.
     *
     * <p>Rethrows any exception thrown by the action.</p>
     */
    <T> T call(CallableBuildOperation<T> buildOperation);

    /**
     * Submits an arbitrary number of runnable operations, created synchronously by the scheduling action, to be executed in the global
     * build operation thread pool. Operations may execute concurrently. Blocks until all operations are complete.
     */
    <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction);

    /**
     * Submits an arbitrary number of operations, created synchronously by the scheduling action, to be executed by the supplied
     * worker in the global build operation thread pool. Operations may execute concurrently, so the worker should be thread-safe.
     * Blocks until all operations are complete.
     */
    <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction);

    /**
     * Returns the state of the build operation currently running on this thread. Can be used as parent of a new build operation
     * started in a different thread (or process). See {@link org.gradle.internal.progress.BuildOperationDescriptor.Builder#parent(BuildOperationState)}
     *
     * @throws IllegalStateException When the current thread is not executing an operation.
     */
    BuildOperationState getCurrentOperation();
}
