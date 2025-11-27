/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildtree;

import com.google.common.base.Preconditions;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ServiceScope(Scope.BuildTree.class)
public interface BuildTreeWorkController {

    /**
     * Schedules and runs requested tasks based on the provided {@code taskSelector}.
     */
    TaskRunResult scheduleAndRunRequestedTasks(@Nullable EntryTaskSelector taskSelector);

    class TaskRunResult {
        private final ExecutionResult<Void> scheduleResult;
        @Nullable
        private final ExecutionResult<Void> executionResult;

        private TaskRunResult(ExecutionResult<Void> scheduleResult, @Nullable ExecutionResult<Void> executionResult) {
            this.scheduleResult = scheduleResult;
            this.executionResult = executionResult;
        }

        /**
         * Returns the result of task configuration and schedule.
         */
        public ExecutionResult<Void> getScheduleResult() {
            return scheduleResult;
        }

        /**
         * Returns the result of task execution. If task execution result is not present, it means task configuration and schedule failed and this will throw an exception.
         */
        public ExecutionResult<Void> getExecutionResultOrThrow() {
            scheduleResult.rethrow();
            return Preconditions.checkNotNull(executionResult);
        }

        /**
         * Returns the result of a task execution. If task configuration and schedule failed, this will return an empty result.
         */
        public Optional<ExecutionResult<Void>> getExecutionResult() {
            return Optional.ofNullable(executionResult);
        }

        public static TaskRunResult ofExecutionResult(ExecutionResult<Void> executionResult) {
            return new TaskRunResult(ExecutionResult.succeeded(), executionResult);
        }

        public static TaskRunResult ofScheduleFailure(Throwable scheduleResult) {
            return new TaskRunResult(ExecutionResult.failed(scheduleResult), null);
        }
    }
}
