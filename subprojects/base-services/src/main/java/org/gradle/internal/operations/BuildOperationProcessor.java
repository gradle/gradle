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


import org.gradle.api.Nullable;

/**
 * A processor for executing build operations.
 */
public interface BuildOperationProcessor {
    /**
     * Creates a new queue for holding operations to be executed.
     *
     * @param worker The action to be executed for each operation.
     * @param <T> The type of operations the worker uses.
     * @return A queue to add operations to and wait for their completion.
     */
    <T extends BuildOperation> BuildOperationQueue<T> newQueue(BuildOperationWorker<T> worker, @Nullable String logLocation);
}
