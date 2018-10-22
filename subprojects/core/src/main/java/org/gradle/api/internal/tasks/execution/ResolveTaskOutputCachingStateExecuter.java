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

package org.gradle.api.internal.tasks.execution;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.DefaultTaskOutputCachingState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory.*;

public class ResolveTaskOutputCachingStateExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskOutputCachingStateExecuter.class);

    private static final TaskOutputCachingState ENABLED = DefaultTaskOutputCachingState.enabled();
    private static final TaskOutputCachingState DISABLED = DefaultTaskOutputCachingState.disabled(BUILD_CACHE_DISABLED, "Task output caching is disabled");
    private static final TaskOutputCachingState CACHING_NOT_ENABLED = DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK, "Caching has not been enabled for the task");
    private static final TaskOutputCachingState NO_OUTPUTS_DECLARED = DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED, "No outputs declared");

    private final boolean buildCacheEnabled;
    private final TaskExecuter delegate;

    public ResolveTaskOutputCachingStateExecuter(boolean buildCacheEnabled, TaskExecuter delegate) {
        this.buildCacheEnabled = buildCacheEnabled;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (buildCacheEnabled) {
            TaskOutputCachingState taskOutputCachingState = resolveCachingState(
                context.getTaskProperties().hasDeclaredOutputs(),
                context.getTaskProperties().getOutputFileProperties(),
                context.getBuildCacheKey(),
                task,
                task.getOutputs().getCacheIfSpecs(),
                task.getOutputs().getDoNotCacheIfSpecs(),
                context.getTaskArtifactState().getOverlappingOutputs()
            );
            state.setTaskOutputCaching(taskOutputCachingState);
            if (!taskOutputCachingState.isEnabled()) {
                LOGGER.info("Caching disabled for {}: {}", task, taskOutputCachingState.getDisabledReason());
            }
        } else {
            state.setTaskOutputCaching(DISABLED);
        }
        return delegate.execute(task, state, context);
    }

    @VisibleForTesting
    static TaskOutputCachingState resolveCachingState(
        boolean hasDeclaredOutputs,
        Collection<TaskOutputFilePropertySpec> outputFileProperties,
        TaskOutputCachingBuildCacheKey buildCacheKey,
        TaskInternal task,
        Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs,
        Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs,
        @Nullable OverlappingOutputs overlappingOutputs
    ) {
        if (cacheIfSpecs.isEmpty()) {
            return CACHING_NOT_ENABLED;
        }

        if (!hasDeclaredOutputs) {
            return NO_OUTPUTS_DECLARED;
        }

        if (overlappingOutputs != null) {
            return DefaultTaskOutputCachingState.disabled(TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS,
                String.format("Gradle does not know how file '%s' was created (output property '%s'). Task output caching requires exclusive access to output paths to guarantee correctness.",
                    overlappingOutputs.getOverlappedFilePath(), overlappingOutputs.getPropertyName()));
        }

        for (TaskOutputFilePropertySpec spec : outputFileProperties) {
            if (!(spec instanceof CacheableTaskOutputFilePropertySpec)) {
                return DefaultTaskOutputCachingState.disabled(
                    NON_CACHEABLE_TREE_OUTPUT,
                    "Output property '"
                        + spec.getPropertyName()
                        + "' contains a file tree"
                );
            }
        }

        for (SelfDescribingSpec<TaskInternal> cacheIfSpec : cacheIfSpecs) {
            if (!cacheIfSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled(
                    CACHE_IF_SPEC_NOT_SATISFIED,
                    "'" + cacheIfSpec.getDisplayName() + "' not satisfied"
                );
            }
        }

        for (SelfDescribingSpec<TaskInternal> doNotCacheIfSpec : doNotCacheIfSpecs) {
            if (doNotCacheIfSpec.isSatisfiedBy(task)) {
                return DefaultTaskOutputCachingState.disabled(
                    DO_NOT_CACHE_IF_SPEC_SATISFIED,
                    "'" + doNotCacheIfSpec.getDisplayName() + "' satisfied"
                );
            }
        }

        if (!buildCacheKey.isValid()) {
            return getCachingStateForInvalidCacheKey(buildCacheKey);
        }
        return ENABLED;
    }

    private static TaskOutputCachingState getCachingStateForInvalidCacheKey(TaskOutputCachingBuildCacheKey buildCacheKey) {
        BuildCacheKeyInputs buildCacheKeyInputs = buildCacheKey.getInputs();
        ImplementationSnapshot taskImplementation = buildCacheKeyInputs.getTaskImplementation();
        if (taskImplementation != null && taskImplementation.isUnknown()) {
            return DefaultTaskOutputCachingState.disabled(NON_CACHEABLE_TASK_IMPLEMENTATION, "Task class " + taskImplementation.getUnknownReason());
        }

        List<ImplementationSnapshot> actionImplementations = buildCacheKeyInputs.getActionImplementations();
        if (actionImplementations != null && !actionImplementations.isEmpty()) {
            for (ImplementationSnapshot actionImplementation : actionImplementations) {
                if (actionImplementation.isUnknown()) {
                    return DefaultTaskOutputCachingState.disabled(NON_CACHEABLE_TASK_ACTION, "Task action " + actionImplementation.getUnknownReason());
                }
            }
        }

        ImmutableSortedMap<String, String> invalidInputProperties = buildCacheKeyInputs.getNonCacheableInputProperties();
        if (invalidInputProperties != null && !invalidInputProperties.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Non-cacheable inputs: ");
            boolean first = true;
            for (Map.Entry<String, String> entry : Preconditions.checkNotNull(invalidInputProperties).entrySet()) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder
                    .append("property '")
                    .append(entry.getKey())
                    .append("' ")
                    .append(entry.getValue());
            }
            return DefaultTaskOutputCachingState.disabled(
                NON_CACHEABLE_INPUTS,
                builder.toString()
            );
        }
        throw new IllegalStateException("Cache key is invalid without a known reason: " + buildCacheKey);
    }
}
