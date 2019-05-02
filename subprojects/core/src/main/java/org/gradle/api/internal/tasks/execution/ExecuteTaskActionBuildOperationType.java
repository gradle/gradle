/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.internal.operations.BuildOperationType;

import javax.annotation.Nullable;

/**
 * The execution of a single task action.
 */
public final class ExecuteTaskActionBuildOperationType implements BuildOperationType<ExecuteTaskActionBuildOperationType.Details, ExecuteTaskActionBuildOperationType.Result> {

    public interface Details {
    }

    public interface Result {
        /**
         * Provides the time spent waiting for work items and/or locks after the task action has completed.
         * @return the post-action wait time, or `null` if no wait was required.
         */
        @Nullable
        Wait getPostActionWait();

        interface Wait {
            long getStartTime();
            long getDuration();
        }
    }

    private ExecuteTaskActionBuildOperationType() {
    }
}
