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
package org.gradle.api.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.internal.exceptions.Contextual;

/**
 * <p>A {@code TaskExecutionException} is thrown when a task fails to execute successfully.</p>
 */
@Contextual
public class TaskExecutionException extends GradleException {
    private final Task task;

    public TaskExecutionException(Task task, Throwable cause) {
        super(String.format("Execution failed for %s.", task), cause);
        this.task = task;
    }

    public Task getTask() {
        return task;
    }
}
