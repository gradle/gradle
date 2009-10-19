/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.Task;
import org.gradle.api.GradleException;

class DefaultTaskExecutionResult implements TaskExecutionResult {
    private final Task task;
    private final Throwable failure;
    private final String skipMessage;

    public DefaultTaskExecutionResult(Task task, Throwable failure, String skipMessage) {
        this.task = task;
        this.failure = failure;
        this.skipMessage = skipMessage;
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
        throw new GradleException(String.format("Task %s failed with an exception.", task), failure);
    }

    public String getSkipMessage() {
        return skipMessage;
    }
}
