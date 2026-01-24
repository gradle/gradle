/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.concurrent.ManagedExecutor;

/**
 * Details related to a given {@link BuildOperationConstraint}.
 */
public class BuildOperationExecutionContext {

    private final ManagedExecutor executor;
    private final int maxConcurrency;
    private final boolean requiresWorkerLease;

    public BuildOperationExecutionContext(
        ManagedExecutor executor,
        int maxConcurrency,
        boolean requiresWorkerLease
    ) {
        this.executor = executor;
        this.maxConcurrency = maxConcurrency;
        this.requiresWorkerLease = requiresWorkerLease;
    }

    /**
     * The executor to use for executing build operations.
     */
    public ManagedExecutor getExecutor() {
        return executor;
    }

    /**
     * The maximum number of concurrent operations that can be executed.
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * Whether constrained build operations require a worker lease.
     */
    public boolean requiresWorkerLease() {
        return requiresWorkerLease;
    }

}
