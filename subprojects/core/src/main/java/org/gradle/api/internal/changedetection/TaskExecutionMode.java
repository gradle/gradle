/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Keeps information about the execution mode of a task.
 */
public enum TaskExecutionMode {
    INCREMENTAL(null, true, true),
    NO_OUTPUTS_WITHOUT_ACTIONS("Task has not declared any outputs nor actions.", false, false),
    NO_OUTPUTS_WITH_ACTIONS("Task has not declared any outputs despite executing actions.", false, false),
    RERUN_TASKS_ENABLED("Executed with '--rerun-tasks'.", true, false),
    UP_TO_DATE_WHEN_FALSE("Task.upToDateWhen is false.", true, false);

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<String> rebuildReason;
    private final boolean taskHistoryMaintained;
    private final boolean allowedToUseCachedResults;

    TaskExecutionMode(@Nullable String rebuildReason, boolean taskHistoryMaintained, boolean allowedToUseCachedResults) {
        this.rebuildReason = Optional.ofNullable(rebuildReason);
        this.taskHistoryMaintained = taskHistoryMaintained;
        this.allowedToUseCachedResults = allowedToUseCachedResults;
    }

    /**
     * Return rebuild reason if any.
     */
    public Optional<String> getRebuildReason() {
        return rebuildReason;
    }

    /**
     * Returns whether the execution history should be stored.
     */
    public boolean isTaskHistoryMaintained() {
        return taskHistoryMaintained;
    }

    /**
     * Returns whether it is okay to use results loaded from cache instead of executing the task.
     */
    public boolean isAllowedToUseCachedResults() {
        return allowedToUseCachedResults;
    }
}
