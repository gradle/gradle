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

import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.execution.ExecuteTaskBuildOperationType;
import org.gradle.internal.id.UniqueId;

import javax.annotation.Nullable;
import java.util.List;

public class ExecuteTaskBuildOperationResult implements ExecuteTaskBuildOperationType.Result {

    private final TaskStateInternal taskState;
    private final TaskExecutionContext ctx;

    public ExecuteTaskBuildOperationResult(TaskStateInternal taskState, TaskExecutionContext ctx) {
        this.taskState = taskState;
        this.ctx = ctx;
    }

    @Nullable
    @Override
    public String getSkipMessage() {
        return taskState.getSkipMessage();
    }

    @Override
    public boolean isActionable() {
        return taskState.isActionable();
    }

    @Nullable
    @Override
    public String getOriginBuildInvocationId() {
        UniqueId originBuildInvocationId = ctx.getOriginBuildInvocationId();
        return originBuildInvocationId == null ? null : originBuildInvocationId.asString();
    }

    @Nullable
    @Override
    public String getCachingDisabledReasonMessage() {
        TaskOutputCachingState taskOutputCaching = taskState.getTaskOutputCaching();
        return taskOutputCaching.getDisabledReason();
    }

    @Nullable
    @Override
    public String getCachingDisabledReasonCategory() {
        TaskOutputCachingState taskOutputCaching = taskState.getTaskOutputCaching();
        TaskOutputCachingDisabledReasonCategory disabledReasonCategory = taskOutputCaching.getDisabledReasonCategory();
        return disabledReasonCategory == null ? null : disabledReasonCategory.name();

    }

    @Nullable
    @Override
    public List<String> getUpToDateMessages() {
        return ctx.getUpToDateMessages();
    }

}
