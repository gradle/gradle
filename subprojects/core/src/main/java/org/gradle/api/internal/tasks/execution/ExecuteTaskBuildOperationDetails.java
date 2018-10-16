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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;

public class ExecuteTaskBuildOperationDetails implements ExecuteTaskBuildOperationType.Details, CustomOperationTraceSerialization {

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
        return task.getTaskIdentity().buildPath.toString();
    }

    @Override
    public String getTaskPath() {
        return task.getTaskIdentity().projectPath.toString();
    }

    @Override
    public long getTaskId() {
        return task.getTaskIdentity().uniqueId;
    }

    @Override
    public Class<?> getTaskClass() {
        return task.getTaskIdentity().type;
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<String, Object>();
        builder.put("buildPath", getBuildPath());
        builder.put("taskPath", getTaskPath());
        builder.put("taskClass", getTaskClass().getName());
        builder.put("taskId", getTaskId());
        return builder.build();

    }
}
