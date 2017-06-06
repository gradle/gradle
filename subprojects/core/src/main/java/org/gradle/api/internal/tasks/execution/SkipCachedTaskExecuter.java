/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.tasks.TaskBuildCacheCommandFactory;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedSet;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final BuildCacheController buildCache;
    private final TaskExecuter delegate;
    private final TaskOutputsGenerationListener taskOutputsGenerationListener;
    private final TaskBuildCacheCommandFactory buildCacheCommandFactory;

    public SkipCachedTaskExecuter(
        BuildCacheController buildCache,
        TaskOutputsGenerationListener taskOutputsGenerationListener,
        TaskBuildCacheCommandFactory buildCacheCommandFactory,
        TaskExecuter delegate
    ) {
        this.taskOutputsGenerationListener = taskOutputsGenerationListener;
        this.buildCacheCommandFactory = buildCacheCommandFactory;
        this.buildCache = buildCache;
        this.delegate = delegate;
    }

    @Override
    public void execute(final TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        final Timer clock = Timers.startTimer();

        LOGGER.debug("Determining if {} is cached already", task);

        final TaskOutputsInternal taskOutputs = task.getOutputs();
        TaskOutputCachingBuildCacheKey cacheKey = context.getBuildCacheKey();
        boolean taskOutputCachingEnabled = state.getTaskOutputCaching().isEnabled();

        SortedSet<ResolvedTaskOutputFilePropertySpec> outputProperties = null;
        if (taskOutputCachingEnabled) {
            if (task.isHasCustomActions()) {
                LOGGER.info("Custom actions are attached to {}.", task);
            }
            if (cacheKey.isValid()) {
                TaskArtifactState taskState = context.getTaskArtifactState();
                // TODO: This is really something we should do at an earlier/higher level so that the input and output
                // property values are locked in at this point.
                outputProperties = TaskPropertyUtils.resolveFileProperties(taskOutputs.getFileProperties());
                if (taskState.isAllowedToUseCachedResults()) {
                    TaskOutputOriginMetadata originMetadata = buildCache.load(
                        buildCacheCommandFactory.load(cacheKey, outputProperties, task, taskOutputsGenerationListener, clock)
                    );
                    if (originMetadata != null) {
                        state.setOutcome(TaskExecutionOutcome.FROM_CACHE);
                        context.setOriginBuildInvocationId(originMetadata.getBuildInvocationId());
                        return;
                    }
                } else {
                    LOGGER.info("Not loading {} from cache because pulling from cache is disabled for this task", task);
                }
            } else {
                LOGGER.info("Not caching {} because no valid cache key was generated", task);
            }
        }

        delegate.execute(task, state, context);

        if (taskOutputCachingEnabled) {
            if (cacheKey.isValid()) {
                if (state.getFailure() == null) {
                    buildCache.store(buildCacheCommandFactory.store(cacheKey, outputProperties, task, clock));
                } else {
                    LOGGER.debug("Not pushing result from {} to cache because the task failed", task);
                }
            } else {
                LOGGER.info("Not pushing results from {} to cache because no valid cache key was generated", task);
            }
        }
    }

}
