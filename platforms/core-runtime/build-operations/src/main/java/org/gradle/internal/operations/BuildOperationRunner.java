/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * Runs build operations: the pieces of work that make up a build.
 * Build operations can be nested inside other build operations.
 */
@ServiceScope(Scope.Global.class)
public interface BuildOperationRunner {
    /**
     * Runs the given build operation.
     *
     * <p>Rethrows any exception thrown by the action.
     * Runtime exceptions are rethrown as is.
     * Checked exceptions are wrapped in {@link BuildOperationInvocationException}.</p>
     */
    void run(RunnableBuildOperation buildOperation);

    /**
     * Calls the given build operation, returns the result.
     *
     * <p>Rethrows any exception thrown by the action.
     * Runtime exceptions are rethrown as is.
     * Checked exceptions are wrapped in {@link BuildOperationInvocationException}.</p>
     */
    <T> T call(CallableBuildOperation<T> buildOperation);

    /**
     * Starts an operation that can be finished later.
     *
     * When a parent operation is finished any unfinished child operations will be failed.
     */
    BuildOperationContext start(BuildOperationDescriptor.Builder descriptor);

    /**
     * Executes the given build operation with the given worker, returns the result.
     */
    <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent);

    /**
     * Returns the state of the build operation currently running on this thread. Can be used as parent of a new build operation
     * started in a different thread (or process). See {@link BuildOperationDescriptor.Builder#parent(BuildOperationRef)}
     *
     * @throws IllegalStateException When the current thread is not executing an operation.
     */
    BuildOperationRef getCurrentOperation();
}
