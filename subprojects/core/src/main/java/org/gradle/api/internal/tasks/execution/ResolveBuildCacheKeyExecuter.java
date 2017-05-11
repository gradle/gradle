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
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.internal.ComputeTaskInputsHashesAndBuildCacheKeyDetails;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.caching.internal.tasks.TaskOutputCachingListener;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveBuildCacheKeyExecuter implements TaskExecuter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveBuildCacheKeyExecuter.class);

    private final TaskOutputCachingListener listener;
    private final TaskExecuter delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public ResolveBuildCacheKeyExecuter(TaskOutputCachingListener listener, TaskExecuter delegate, BuildOperationExecutor buildOperationExecutor) {
        this.listener = listener;
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        resolve(task, context);
        delegate.execute(task, state, context);
    }

    private void resolve(final TaskInternal task, final TaskExecutionContext context) {
        /*
            This operation represents the work of analyzing the inputs.
            Therefore, it should encompass all of the file IO and compute necessary to do this.
            This effectively happens in the first call to context.getTaskArtifactState().getStates().
            If build caching is enabled, this is the first time that this will be called so it effectively
            encapsulates this work.

            If build cache isn't enabled, this executer isn't in the mix and therefore the work of hashing
            the inputs will happen later in the executer chain, and therefore they aren't wrapped in an operation.
            We avoid adding this executer if build caching is not enabled due to concerns of performance impact

            So, later, we either need always have this executer in the mix or make the input hashing
            an explicit step that always happens earlier and wrap it.
            Regardless, it would be good to formalise the input work in some form so it doesn't just
            happen as a side effect of calling some method for the first time.
         */
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext buildOperationContext) {
                TaskOutputCachingBuildCacheKey cacheKey = doResolve(task, context);
                buildOperationContext.setResult(cacheKey);
                context.setBuildCacheKey(cacheKey);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Compute task input hashes and build cache key")
                    .details(new ComputeTaskInputsHashesAndBuildCacheKeyDetails());
            }
        });
    }

    private TaskOutputCachingBuildCacheKey doResolve(TaskInternal task, TaskExecutionContext context) {
        TaskArtifactState taskState = context.getTaskArtifactState();
        TaskOutputCachingBuildCacheKey cacheKey = taskState.calculateCacheKey();
        if (task.getOutputs().getHasOutput()) { // A task with no outputs an no cache key.
            listener.cacheKeyEvaluated(task, cacheKey);
            if (cacheKey.isValid()) {
                LOGGER.info("Cache key for {} is {}", task, cacheKey.getHashCode());
            }
        }
        return cacheKey;
    }
}
