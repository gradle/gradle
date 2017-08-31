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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.execution.ExecuteTaskBuildOperationType;

public class ExecuteTaskBuildOperationDetails implements ExecuteTaskBuildOperationType.Details {

    private final TaskInternal task;

    public ExecuteTaskBuildOperationDetails(TaskInternal task) {
        this.task = task;
    }

    // TODO: do not reference mutable state
    // Note: internal, not used by scans plugin
    public TaskInternal getTask() {
        return task;
    }

    @Override
    public String getBuildPath() {
        return ((GradleInternal) task.getProject().getGradle()).getIdentityPath().toString();
    }

    @Override
    public String getTaskPath() {
        return task.getPath();
    }

    @Override
    public long getTaskId() {
        return (long) System.identityHashCode(task);
    }

    @Override
    public Class<?> getTaskClass() {
        return GeneratedSubclasses.unpack(task.getClass());
    }

}
