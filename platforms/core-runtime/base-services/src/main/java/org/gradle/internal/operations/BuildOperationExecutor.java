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

import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Executes build operations via a {@link BuildOperationQueue}.
 */
@ThreadSafe
@ServiceScope(Scope.CrossBuildSession.class)
public interface BuildOperationExecutor {
    /**
     * Submits an arbitrary number of runnable operations, created synchronously by the scheduling action, to be executed in the global
     * build operation thread pool constrained to {@link BuildOperationConstraint#MAX_WORKERS}. Operations may execute concurrently. Blocks until all operations are complete.
     *
     * <p>Actions are not permitted to access any mutable project state. Generally, this is preferred.</p>
     */
    <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction);

    /**
     * Overload allowing {@link BuildOperationConstraint} to be specified.
     *
     * @see BuildOperationExecutor#runAllWithAccessToProjectState(Action)
     */
    <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint);

    /**
     * Same as {@link #runAll(Action)}. However, the actions are allowed to access mutable project state. In general, this is more likely to
     * result in deadlocks and other flaky behaviours.
     *
     * <p>See {@link org.gradle.internal.resources.ProjectLeaseRegistry#whileDisallowingProjectLockChanges(Factory)} for more details.
     */
    <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction);

    /**
     * Overload allowing {@link BuildOperationConstraint} to be specified.
     *
     * @see BuildOperationExecutor#runAllWithAccessToProjectState(Action)
     */
    <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint);

    /**
     * Submits an arbitrary number of operations, created synchronously by the scheduling action, to be executed by the supplied
     * worker in the global build operation thread pool. Operations may execute concurrently, so the worker should be thread-safe.
     * Blocks until all operations are complete.
     *
     * <p>Actions are not permitted to access any mutable project state. Generally, this is preferred.</p>
     */
    <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction);

    /**
     * Overload allowing {@link BuildOperationConstraint} to be specified.
     *
     * @see BuildOperationExecutor#runAll(BuildOperationWorker, Action)
     */
    <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint);

    @Deprecated
    BuildOperationRef getCurrentOperation();
}
