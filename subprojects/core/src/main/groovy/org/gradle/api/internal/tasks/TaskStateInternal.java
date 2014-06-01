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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskState;

public class TaskStateInternal implements TaskState {
    private boolean executing;
    private boolean executed;
    private boolean didWork;
    private Throwable failure;
    private String description;
    private String skippedMessage;
    private boolean skipped;

    public TaskStateInternal(String description) {
        this.description = description;
    }

    public boolean getDidWork() {
        return didWork;
    }

    public void setDidWork(boolean didWork) {
        this.didWork = didWork;
    }

    public boolean getExecuted() {
        return executed;
    }

    /**
     * Marks this task as executed. This method can be called multiple times.
     */
    public void executed() {
        this.executed = true;
    }

    public boolean isConfigurable(){
        return !executed && !executing;
    }

    /**
     * Marks this task as executed with the given failure. This method can be called at most once.
     */
    public void executed(Throwable failure) {
        assert this.failure == null;
        this.executed = true;
        this.failure = failure;
    }

    /**
     * Marks this task as skipped.
     */
    public void skipped(String skipMessage) {
        this.executed = true;
        skipped = true;
        this.skippedMessage = skipMessage;
    }

    /**
     * Marks this task as up-to-date.
     */
    public void upToDate() {
        skipped("UP-TO-DATE");
    }
    
    public boolean getExecuting() {
        return executing;
    }

    public void setExecuting(boolean executing) {
        this.executing = executing;
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
        throw new GradleException(String.format("%s failed with an exception.", StringUtils.capitalize(description)), failure);
    }

    public boolean getSkipped() {
        return skipped;
    }

    public String getSkipMessage() {
        return skippedMessage;
    }
}
