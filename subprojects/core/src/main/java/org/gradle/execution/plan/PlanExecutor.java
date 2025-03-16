/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.Action;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Responsible for running the work of a build tree, packaged as zero or more {@link ExecutionPlan} instances.
 */
@ServiceScope(Scope.BuildTree.class)
@ThreadSafe
public interface PlanExecutor {
    /**
     * Executes a {@link WorkSource}, blocking until complete.
     *
     * @param workSource the work to execute.
     * @param worker the actual executor responsible to execute the nodes. Must be thread-safe.
     */
    <T> ExecutionResult<Void> process(WorkSource<T> workSource, Action<T> worker);

    /**
     * Verifies that this executor and the work it is running is healthy (not starved or deadlocked). Aborts any current work when not healthy, so that {@link #process(WorkSource, Action)}
     * returns with a failure result.
     *
     * <p>Note that this method is intended to be called periodically, but is not guaranteed to be particularly efficient, so should not be called too frequently (say more often than every 10 seconds).</p>
     */
    void assertHealthy();
}
