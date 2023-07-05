/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.operations.execution;

import org.gradle.internal.operations.BuildOperationType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A {@link BuildOperationType} for executing any kind of work inside the execution engine.
 * <p>
 * This operation encompasses most of the execution engine pipeline, starting immediately
 * after the work is identified and a workspace is assigned.
 * <p>
 * Note that the pipeline does not have to execute fully, for instance if the work is up-to-date.
 * The underlying work (task or transform action) can be skipped, and the {@link Result#getSkipMessage() skip message}
 * is provided in the build operation result.
 *
 * @since 8.3
 */
public final class ExecuteWorkBuildOperationType implements BuildOperationType<ExecuteWorkBuildOperationType.Details, ExecuteWorkBuildOperationType.Result> {

    public interface Details {

        /**
         * Type of work being executed.
         * <p>
         * Expected values are:
         * <ul>
         *     <li>{@code null} - work type is not classified</li>
         *     <li>{@code TRANSFORM} - execution of an artifact transform</li>
         * </ul>
         */
        @Nullable
        String getWorkType();

        /**
         * The identity of the executed unit of work.
         */
        String getIdentity();
    }

    public interface Result {

        /**
         * A message describing why the work was skipped.
         * <p>
         * Expected values are:
         * <ul>
         *     <li>{@code null} - the work was not skipped</li>
         *     <li>{@code NO-SOURCE} - executing the work was no necessary to produce the outputs</li>
         *     <li>{@code UP-TO-DATE} - the outputs have not changed, because the work is already up-to-date</li>
         *     <li>{@code FROM-CACHE} - the outputs have been loaded from the build cache</li>
         * </ul>
         */
        @Nullable
        String getSkipMessage();

        /**
         * If work was UP_TO_DATE or FROM_CACHE, this will convey the ID of the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the work executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        @Nullable
        String getOriginBuildInvocationId();

        /**
         * If the work was UP_TO_DATE or FROM_CACHE, this will convey the execution time of the work in the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the work executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism,
         * but the unit of work returns {@code false} for {@code org.gradle.internal.execution.UnitOfWork.WorkOutput#getDidWork()}.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        @Nullable
        Long getOriginExecutionTime();

        /**
         * The human friendly description of why this work was not cacheable.
         * Null if the work was cacheable.
         * Not null if {@link #getCachingDisabledReasonCategory()} is not null.
         */
        @Nullable
        String getCachingDisabledReasonMessage();

        /**
         * The categorisation of why the work was not cacheable.
         * Null if the work was cacheable.
         * Not null if {@link #getCachingDisabledReasonMessage()}l is not null.
         * Values are expected to correlate to {@link org.gradle.operations.execution.CachingDisabledReasonCategory}.
         */
        @Nullable
        String getCachingDisabledReasonCategory();

        /**
         * Opaque messages describing why the work was executed.
         * <p>
         * In the order emitted by Gradle.
         * Null if execution did not get so far as to test “up-to-date-ness”.
         * Empty if tested, but work was considered up to date.
         */
        List<String> getExecutionReasons();
    }
}
