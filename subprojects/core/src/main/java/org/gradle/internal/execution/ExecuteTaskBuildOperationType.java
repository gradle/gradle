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

package org.gradle.internal.execution;

import org.gradle.api.Nullable;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;

/**
 * Important note: the scan listener currently expects to receive the operation started notification on the
 * _same thread_ that is about to execute the task. If this changes (e.g. notifications are dispatched async),
 * additional changes need to be made to convey the thread/worker that is going to be used to execute the task.
 */
public final class ExecuteTaskBuildOperationType implements BuildOperationType<ExecuteTaskBuildOperationType.Details, ExecuteTaskBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        String getBuildPath();

        String getTaskPath();

        String getTaskClassName();

    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * The message describing why the task was skipped.
         *
         * Expected to be {@link org.gradle.api.tasks.TaskState#getSkipMessage()}.
         */
        @Nullable
        String getSkipMessage();

        /**
         * Whether the task had any actions.
         * See {@link org.gradle.api.internal.tasks.TaskStateInternal#isActionable()}.
         */
        boolean isActionable();

        /**
         * If task was UP_TO_DATE or FROM_CACHE, this will convey the ID of the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         */
        @Nullable
        String getOriginBuildId();

        /**
         * The human friendly description of why this task was not cacheable.
         * Null if the task was cacheable.
         * Not null if {@link #getCachingDisabledReasonCategory()} is not null.
         */
        @Nullable
        String getCachingDisabledReasonMessage();

        /**
         * The categorisation of the why the task was not cacheable.
         * Null if the task was cacheable.
         * Not null if {@link #getCachingDisabledReasonMessage()}l is not null.
         * Values are expected to correlate to {@link org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory}.
         */
        @Nullable
        String getCachingDisabledReasonCategory();

        /**
         * Opaque messages describing why the task was not up to date.
         * In the order emitted by Gradle.
         * Null if execution did not get so far as to test “up-to-date-ness”.
         * Empty if tested, but task was considered up to date.
         */
        @Nullable
        List<String> getUpToDateMessages();

    }

    private ExecuteTaskBuildOperationType() {

    }

}
