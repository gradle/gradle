/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.tasks.TaskState;

public class TaskStateInternal implements TaskState {
    private boolean executing;
    private boolean actionable = true;
    private boolean didWork;
    private Throwable failure;
    private TaskOutputCachingState taskOutputCaching = DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.UNKNOWN, "Cacheability was not determined");
    private TaskExecutionOutcome outcome;

    public boolean getDidWork() {
        return didWork;
    }

    public void setDidWork(boolean didWork) {
        this.didWork = didWork;
    }

    public boolean getExecuted() {
        return outcome != null;
    }

    public boolean isConfigurable() {
        return !getExecuted() && !executing;
    }

    public TaskExecutionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TaskExecutionOutcome outcome) {
        assert this.outcome == null;
        this.outcome = outcome;
    }

    /**
     * Marks this task as executed with the given failure. This method can be called at most once.
     */
    public void setOutcome(Throwable failure) {
        assert this.failure == null;
        this.outcome = TaskExecutionOutcome.EXECUTED;
        this.failure = failure;
    }

    public boolean getExecuting() {
        return executing;
    }

    public void setExecuting(boolean executing) {
        this.executing = executing;
    }

    public void setTaskOutputCaching(TaskOutputCachingState taskOutputCaching) {
        this.taskOutputCaching = taskOutputCaching;
    }

    public TaskOutputCachingState getTaskOutputCaching() {
        return taskOutputCaching;
    }

    public Throwable getFailure() {
        return failure;
    }

    public void rethrowFailure() {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new GradleException("Task failed with an exception.", failure);
    }

    public boolean getSkipped() {
        return outcome != null && outcome.isSkipped();
    }

    public String getSkipMessage() {
        return outcome != null ? outcome.getMessage() : null;
    }

    public boolean getUpToDate() {
        return outcome != null && outcome.isUpToDate();
    }

    @Override
    public boolean getNoSource() {
        return outcome == TaskExecutionOutcome.NO_SOURCE;
    }

    public boolean isFromCache() {
        return outcome == TaskExecutionOutcome.FROM_CACHE;
    }

    public boolean isActionable() {
        return actionable;
    }

    public void setActionable(boolean actionable) {
        this.actionable = actionable;
    }

}
