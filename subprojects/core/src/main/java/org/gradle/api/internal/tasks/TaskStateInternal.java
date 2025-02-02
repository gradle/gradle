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

import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.TaskState;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class TaskStateInternal implements TaskState {
    private boolean executing;
    private boolean actionable = true;
    private boolean didWork;
    private RuntimeException failure;
    private volatile TaskExecutionOutcome outcome;

    @Nullable
    private String skipReasonMessage;

    @Override
    public boolean getDidWork() {
        return didWork;
    }

    public void setDidWork(boolean didWork) {
        this.didWork = didWork;
    }

    @Override
    public boolean getExecuted() {
        return outcome != null;
    }

    public boolean isConfigurable() {
        return !getExecuted() && !executing;
    }

    @Nullable
    public TaskExecutionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TaskExecutionOutcome outcome) {
        assert this.outcome == null;
        this.outcome = outcome;
    }

    /**
     * The detailed reason why the task was skipped, if provided.
     *
     * @return the reason. returns null if the task was not skipped or if the reason was not provided.
     * @see org.gradle.api.Task#onlyIf(String, org.gradle.api.specs.Spec)
     * @since 7.6
     */
    @Nullable
    public String getSkipReasonMessage() {
        return skipReasonMessage;
    }

    /**
     * Sets the detailed reason why the task was skipped.
     *
     * @see TaskStateInternal#getSkipReasonMessage()
     */
    public void setSkipReasonMessage(@Nullable String skipReasonMessage) {
        this.skipReasonMessage = skipReasonMessage;
    }

    /**
     * Marks this task as executed with the given failure. This method can be called at most once.
     */
    public void setOutcome(RuntimeException failure) {
        assert this.failure == null;
        this.outcome = TaskExecutionOutcome.EXECUTED;
        this.failure = failure;
    }

    public void addFailure(TaskExecutionException failure) {
        if (this.failure == null) {
            this.failure = failure;
        } else if (this.failure instanceof TaskExecutionException) {
            TaskExecutionException taskExecutionException = (TaskExecutionException) this.failure;
            List<Throwable> causes = new ArrayList<>(taskExecutionException.getCauses());
            CollectionUtils.addAll(causes, failure.getCauses());
            taskExecutionException.initCauses(causes);
        } else {
            List<Throwable> causes = new ArrayList<>();
            causes.add(this.failure);
            causes.addAll(failure.getCauses());
            failure.initCauses(causes);
            this.failure = failure;
        }
    }

    public boolean getExecuting() {
        return executing;
    }

    public void setExecuting(boolean executing) {
        this.executing = executing;
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public void rethrowFailure() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean getSkipped() {
        return outcome != null && outcome.isSkipped();
    }

    @Override
    public String getSkipMessage() {
        return outcome != null ? outcome.getMessage() : null;
    }

    @Override
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
