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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;

import javax.annotation.Nullable;
import java.util.List;

public class ExecuteTaskBuildOperationResult implements ExecuteTaskBuildOperationType.Result {

    private final TaskStateInternal taskState;
    private final CachingState cachingState;
    private final OriginMetadata originMetadata;
    private final boolean incremental;
    private final List<String> executionReasons;

    public ExecuteTaskBuildOperationResult(TaskStateInternal taskState, CachingState cachingState, @Nullable OriginMetadata originMetadata, boolean incremental, List<String> executionReasons) {
        this.taskState = taskState;
        this.cachingState = cachingState;
        this.originMetadata = originMetadata;
        this.incremental = incremental;
        this.executionReasons = executionReasons;
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
        return originMetadata == null ? null : originMetadata.getBuildInvocationId();
    }

    @Nullable
    @Override
    public Long getOriginExecutionTime() {
        return originMetadata == null ? null : originMetadata.getExecutionTime();
    }

    @Nullable
    @Override
    public String getCachingDisabledReasonMessage() {
        ImmutableList<CachingDisabledReason> disabledReasons = cachingState.getDisabledReasons();
        return disabledReasons.isEmpty()
            ? null
            : disabledReasons.get(0).getMessage();
    }

    @Nullable
    @Override
    public String getCachingDisabledReasonCategory() {
        ImmutableList<CachingDisabledReason> disabledReasons = cachingState.getDisabledReasons();
        return disabledReasons.isEmpty()
            ? null
            : convertNoCacheReasonCategory(disabledReasons.get(0).getCategory()).name();
    }

    private static TaskOutputCachingDisabledReasonCategory convertNoCacheReasonCategory(CachingDisabledReasonCategory category) {
        switch (category) {
            case UNKNOWN:
                return TaskOutputCachingDisabledReasonCategory.UNKNOWN;
            case BUILD_CACHE_DISABLED:
                return TaskOutputCachingDisabledReasonCategory.BUILD_CACHE_DISABLED;
            case NOT_CACHEABLE:
                return TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK;
            case ENABLE_CONDITION_NOT_SATISFIED:
                return TaskOutputCachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED;
            case DISABLE_CONDITION_SATISFIED:
                return TaskOutputCachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED;
            case NO_OUTPUTS_DECLARED:
                return TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED;
            case NON_CACHEABLE_OUTPUT:
                return TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TREE_OUTPUT;
            case OVERLAPPING_OUTPUTS:
                return TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS;
            case NON_CACHEABLE_IMPLEMENTATION:
                return TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TASK_IMPLEMENTATION;
            case NON_CACHEABLE_ADDITIONAL_IMPLEMENTATION:
                return TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TASK_ACTION;
            case NON_CACHEABLE_INPUTS:
                return TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_INPUTS;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public List<String> getUpToDateMessages() {
        return executionReasons;
    }

    @Override
    public boolean isIncremental() {
        return incremental;
    }

}
