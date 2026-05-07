/*
 * Copyright 2021 the original author or authors.
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

/**
 * Constraint to apply to the execution of a {@link BuildOperation}.
 */
public enum BuildOperationConstraint {

    /**
     * Constrain execution by the configured maximum number of workers.
     * <p>
     * Intended for CPU intensive operations. Operations constrained by this are required
     * to acquire a {@link org.gradle.internal.work.WorkerLeaseRegistry worker lease}
     * before proceeding.
     */
    MAX_WORKERS,

    /**
     * Unconstrained execution allowing as many threads as required to a maximum of 10 times the configured workers.
     * <p>
     * Intended for IO intensive operations. Operations constrained by this are not required
     * to acquire a {@link org.gradle.internal.work.WorkerLeaseRegistry worker lease}.
     */
    UNCONSTRAINED

}
