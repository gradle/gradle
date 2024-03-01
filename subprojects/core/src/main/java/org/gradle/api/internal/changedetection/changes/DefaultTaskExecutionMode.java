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
package org.gradle.api.internal.changedetection.changes;

import org.gradle.api.internal.changedetection.TaskExecutionMode;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps information about the execution mode of a task.
 */
public class DefaultTaskExecutionMode implements TaskExecutionMode {

    private static final DefaultTaskExecutionMode UP_TO_DATE_WHEN_FALSE = new DefaultTaskExecutionMode("Task.upToDateWhen is false.", true, false);
    private static final DefaultTaskExecutionMode UNTRACKED_NO_REASON = new DefaultTaskExecutionMode("Task state is not tracked.", false, false);
    private static final DefaultTaskExecutionMode RERUN_TASKS_ENABLED = new DefaultTaskExecutionMode("Executed with '--rerun-tasks'.", true, false);
    private static final DefaultTaskExecutionMode NO_OUTPUTS = new DefaultTaskExecutionMode("Task has not declared any outputs despite executing actions.", false, false);
    private static final DefaultTaskExecutionMode INCREMENTAL = new DefaultTaskExecutionMode(null, true, true);
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<String> rebuildReason;
    private final boolean taskHistoryMaintained;
    private final boolean allowedToUseCachedResults;

    DefaultTaskExecutionMode(@Nullable String rebuildReason, boolean taskHistoryMaintained, boolean allowedToUseCachedResults) {
        this.rebuildReason = Optional.ofNullable(rebuildReason);
        this.taskHistoryMaintained = taskHistoryMaintained;
        this.allowedToUseCachedResults = allowedToUseCachedResults;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getRebuildReason() {
        return rebuildReason;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTaskHistoryMaintained() {
        return taskHistoryMaintained;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAllowedToUseCachedResults() {
        return allowedToUseCachedResults;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultTaskExecutionMode that = (DefaultTaskExecutionMode) o;
        return taskHistoryMaintained == that.taskHistoryMaintained && allowedToUseCachedResults == that.allowedToUseCachedResults && Objects.equals(rebuildReason, that.rebuildReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rebuildReason, taskHistoryMaintained, allowedToUseCachedResults);
    }

    /**
     * The execution mode for incremental tasks.
     */
    public static TaskExecutionMode incremental() {
        return INCREMENTAL;
    }

    /**
     * The execution mode when task did not declare any outputs.
     * The message will be `Task has not declared any outputs despite executing actions.`.
     */
    public static TaskExecutionMode noOutputs() {
        return NO_OUTPUTS;
    }

    /**
     * The execution mode when the command was run with --rerun-tasks.
     * The message will be `Executed with '--rerun-tasks'.`.
     */
    public static TaskExecutionMode rerunTasksEnabled() {
        return RERUN_TASKS_ENABLED;
    }

    /**
     * The execution mode when the Task.upToDateWhen is set to false.
     * The message will be `Task.upToDateWhen is false.`.
     */
    public static TaskExecutionMode upToDateWhenFalse() {
        return UP_TO_DATE_WHEN_FALSE;
    }

    /**
     * The execution mode when the task is marked explicitly untracked.
     * The message will be `Task state is not tracked.`.
     */
    public static TaskExecutionMode untracked() {
        return UNTRACKED_NO_REASON;
    }

    /**
     * The execution mode when the task is marked explicitly untracked.
     * The message will be `"Task is untracked because: " + reason`.
     */
    public static TaskExecutionMode untracked(String reason) {
        return new DefaultTaskExecutionMode("Task is untracked because: " + reason, false, false);
    }
}
