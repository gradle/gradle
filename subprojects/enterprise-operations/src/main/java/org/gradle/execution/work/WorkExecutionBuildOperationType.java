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

package org.gradle.execution.work;

import org.gradle.internal.operations.BuildOperationType;

import java.util.List;
import java.util.Optional;

/**
 * Captures the execution of a unit of work. This includes all operations related to Gradle's bookkeeping and optimizations.
 * Among other things it includes snapshotting inputs and outputs, up-to-date checks, build cache operations.
 * It also wraps the {@link WorkActionExecutionBuildOperationType execution of the action}.
 *
 * The operations can be nested, i.e. when a task executes a worker API action.
 *
 * @see WorkActionExecutionBuildOperationType
 */
public interface WorkExecutionBuildOperationType extends BuildOperationType<WorkExecutionBuildOperationType.Details, WorkExecutionBuildOperationType.Result> {

    interface Details {

        /**
         * The type of work represented by the build operation.
         */
        Class<?> getWorkType();

        /**
         * Path to the owning build.
         */
        String getBuildPath();

        /**
         * A textual identifier for the unit of work when available.
         *
         * Tasks will expose their task paths here. Artifact transforms and workers don't expose anything here.
         */
        Optional<String> getIdentity();

        /**
         * A unique number identifying the unit of work being executed in the context of the current build.
         */
        long getUniqueId();

        /**
         * The type implementing the unit of work.
         */
        Class<?> getImplementation();
    }

    interface Result {
        /**
         * Whether the unit of work had any actions. Lifecycle tasks don't have actions.
         *
         * @see org.gradle.api.internal.tasks.TaskStateInternal#isActionable()
         */
        boolean isActionable();

        /**
         * Returns if this unit of work was executed incrementally.
         *
         * @see org.gradle.work.InputChanges#isIncremental()
         */
        boolean isIncremental();

        /**
         * The message describing why the unit of work was skipped entirely, {@link Optional#empty()} if the task wasn't skipped.
         */
        Optional<String> getSkipMessage();

        /**
         * Opaque messages describing why the work was not up-to-date.
         * In the order emitted by Gradle.
         * {@link Optional#empty()} if execution did not get so far as to test “up-to-date-ness”.
         * Empty list if tested, but task was considered up-to-date.
         */
        Optional<List<String>> getUpToDateMessages();

        /**
         * If the work was {@code UP_TO_DATE} or {@code FROM_CACHE}, this will convey the ID of the build that produced the outputs being reused.
         * Value will be {@link Optional#empty()} for any other outcome.
         *
         * This value may also be {@link Optional#empty()} for an {@code UP_TO_DATE} outcome where the work executed, but then decided it was {@code UP_TO_DATE}.
         * That is, it was not {@code UP_TO_DATE} due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        Optional<String> getOriginBuildInvocationId();

        /**
         * If the work was {@code UP_TO_DATE} or {@code FROM_CACHE}, this will convey the execution time of the work in the build that produced the outputs being reused.
         * Value will be {@link Optional#empty()} for any other outcome.
         *
         * This value may also be {@link Optional#empty()} for an {@code UP_TO_DATE} outcome where the work executed, but then decided it was {@code UP_TO_DATE}.
         * That is, it was not {@code UP_TO_DATE} due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        Optional<Long> getOriginExecutionTime();

        /**
         * The human friendly description of why this work was not cacheable.
         * {@link Optional#empty()} if the work was cacheable.
         * Not empty if {@link #getCachingDisabledReasonCategory()} is not empty.
         */
        Optional<String> getCachingDisabledReasonMessage();

        /**
         * The categorisation of the why the work was not cacheable.
         * {@link Optional#empty()} if the work was cacheable.
         * Not empty if {@link #getCachingDisabledReasonMessage()}l is not empty.
         * Values are expected to correlate to {@link org.gradle.internal.execution.caching.CachingDisabledReasonCategory}.
         */
        Optional<String> getCachingDisabledReasonCategory();
    }

}
