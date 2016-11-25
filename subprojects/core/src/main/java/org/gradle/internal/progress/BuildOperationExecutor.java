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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationContext;

/**
 * Runs build operations. These are the pieces of work that make up a build. Build operations can be nested inside other
 * build operations.
 *
 * The executor provides several capabilities:
 *
 * <ul>
 *     <p>Fires events via {@link InternalBuildListener}. For example, this means that notification of build operation
 *     execution can be received by tooling API clients.</p>
 *     <p>Generates progress logging events.</p>
 * </ul>
 *
 * <p>Operations are executed synchronously.</p>
 *
 * <p>This is intended to be synchronized with {@link org.gradle.internal.operations.BuildOperationProcessor}, to
 * allow both synchronous and asynchronous execution of build operations.
 */
@ThreadSafe
public interface BuildOperationExecutor {
    /**
     * Runs the given build operation synchronously. Invokes the given factory from the current thread and returns the result.
     *
     * <p>Rethrows any exception thrown by the factory.</p>
     */
    <T> T run(String displayName, Transformer<T, ? super BuildOperationContext> factory);

    /**
     * Runs the given build operation synchronously. Invokes the given factory from the current thread and returns the result.
     *
     * <p>Rethrows any exception thrown by the factory.</p>
     */
    <T> T run(BuildOperationDetails operationDetails, Transformer<T, ? super BuildOperationContext> factory);

    /**
     * Runs the given build operation synchronously. Invokes the given action from the current thread.
     *
     * <p>Rethrows any exception thrown by the action.</p>
     */
    void run(String displayName, Action<? super BuildOperationContext> action);

    /**
     * Runs the given build operation synchronously. Invokes the given action from the current thread.
     *
     * <p>Rethrows any exception thrown by the action.</p>
     */
    void run(BuildOperationDetails operationDetails, Action<? super BuildOperationContext> action);

    /**
     * Returns the operation being run by the current thread.
     *
     * @throws IllegalStateException When the current thread is not executing an operation.
     */
    Operation getCurrentOperation();

    /**
     * A handle to an operation. Can be used to reference an operation from several threads to run nested operations.
     */
    interface Operation {
        Object getId();
    }
}
