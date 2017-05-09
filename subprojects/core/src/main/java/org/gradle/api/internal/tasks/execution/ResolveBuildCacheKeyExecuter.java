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
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.caching.internal.tasks.TaskOutputCachingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveBuildCacheKeyExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveBuildCacheKeyExecuter.class);

    private final TaskOutputCachingListener listener;
    private final TaskExecuter delegate;

    public ResolveBuildCacheKeyExecuter(TaskOutputCachingListener listener, TaskExecuter delegate) {
        this.listener = listener;
        this.delegate = delegate;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskArtifactState taskState = context.getTaskArtifactState();
        TaskOutputCachingBuildCacheKey cacheKey = taskState.calculateCacheKey();
        context.setBuildCacheKey(cacheKey);
        if (task.getOutputs().getHasOutput()) { // A task with no outputs an no cache key.
            listener.cacheKeyEvaluated(task, cacheKey);
            if (cacheKey.isValid()) {
                LOGGER.info("Cache key for {} is {}", task, cacheKey.getHashCode());
            }
        }
        delegate.execute(task, state, context);
    }
}
