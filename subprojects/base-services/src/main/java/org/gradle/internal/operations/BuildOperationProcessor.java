/*
 * Copyright 2015 the original author or authors.
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


import org.gradle.api.Action;

/**
 * A processor for executing build operations.
 */
public interface BuildOperationProcessor {
    /**
     * Submits an arbitrary number of operations, created synchronously by the generator, to be executed by the supplied
     * worker in the global build operation thread pool.  Operations may execute concurrently, so the worker should be thread-safe.
     * Blocks until all operations are complete.
     *
     * @param worker The action to be executed for each operation.
     * @param <T> The type of operations the worker uses.
     * @param generator An action that populates the queue with build operations
     */
    <T extends BuildOperation> void run(BuildOperationWorker<T> worker, Action<BuildOperationQueue<T>> generator);

    /**
     * Submits an arbitrary number of runnable operations, created synchronously by the generator, to be executed in the global
     * build operation thread pool.  Operations may execute concurrently.  Blocks until all operations are complete.
     *
     * @param <T> The type of operations the generator produces.
     * @param generator An action that populates the queue with build operations
     */
    <T extends RunnableBuildOperation> void run(Action<BuildOperationQueue<T>> generator);
}
