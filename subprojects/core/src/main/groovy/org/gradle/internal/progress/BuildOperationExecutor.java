/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.internal.Factory;

/**
 * Runs build operations. These are the pieces of work that make up a build.
 *
 * <p>Note that the current implementation is not thread safe. This will happen later.</p>
 *
 * <p>This is to be synchronized with {@link org.gradle.internal.operations.BuildOperationProcessor}.
 */
public interface BuildOperationExecutor {
    /**
     * Runs the given build operation synchronously. Invokes the given factory from the current thread and returns the result.
     *
     * <p>Rethrows any exception thrown by the factory.</p>
     */
    <T> T run(Object id, BuildOperationType operationType, Factory<T> factory);

    /**
     * Runs the given build operation synchronously. Invokes the given action from the current thread.
     *
     * <p>Rethrows any exception thrown by the action.</p>
     */
    void run(Object id, BuildOperationType operationType, Runnable action);

    /**
     * Returns the id of the current operation.
     */
    Object getCurrentOperationId();
}
