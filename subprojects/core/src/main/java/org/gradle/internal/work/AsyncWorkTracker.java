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

package org.gradle.internal.work;

import org.gradle.internal.progress.BuildOperationExecutor.Operation;

/**
 * Allows asynchronous work to be tracked based on the build operation it is associated with.
 */
public interface AsyncWorkTracker {
    /**
     * Register a new item of asynchronous work with the provided build operation.
     *
     * @param operation - The build operation to associate the asynchronous work with
     * @param completion - The completion of the asynchronous work
     */
    void registerWork(Operation operation, AsyncWorkCompletion completion);

    /**
     * Blocks waiting for the completion of all items of asynchronous work associated with the provided build operation.
     *
     * @param operation - The build operation whose asynchronous work should be completed
     */
    void waitForCompletion(Operation operation);
}
