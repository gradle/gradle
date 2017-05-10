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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.ComputeTaskInputsHashesAndBuildCacheKeyDetails;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WrapTaskArtifactAndOutputCachingStatesExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WrapTaskArtifactAndOutputCachingStatesExecuter.class);

    private final TaskExecuter delegate;
    private final TaskExecuter wrappedExecuter;
    private final BuildOperationExecutor buildOpExecutor;

    public WrapTaskArtifactAndOutputCachingStatesExecuter(
        TaskExecuter delegate,
        TaskExecuter wrappedExecuter,
        BuildOperationExecutor buildOpExecutor
    ) {
        this.delegate = delegate;
        this.wrappedExecuter = wrappedExecuter;
        this.buildOpExecutor = buildOpExecutor;
    }

    @Override
    public void execute(final TaskInternal task, final TaskStateInternal state, final TaskExecutionContext taskExecutionContext) {
        buildOpExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext buildOperationContext) {
                wrappedExecuter.execute(task, state, taskExecutionContext);
                buildOperationContext.setResult(taskExecutionContext.getBuildCacheKey());
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Compute task input hashes and build cache key")
                    .details(new ComputeTaskInputsHashesAndBuildCacheKeyDetails());
            }
        });
        if (state.getOutcome() == null) {
            // no need to call the rest of the chain is the task outcome is already known (probably a failure happened in a previous executer)
            delegate.execute(task, state, taskExecutionContext);
        }

    }
}
