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

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.cache.TaskCacheKey;
import org.gradle.api.internal.tasks.cache.TaskOutputCache;
import org.gradle.api.internal.tasks.cache.TaskOutputPacker;
import org.gradle.api.internal.tasks.cache.TaskOutputReader;
import org.gradle.api.internal.tasks.cache.TaskOutputWriter;
import org.gradle.util.Clock;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final TaskArtifactStateRepository repository;
    private final TaskOutputCache cache;
    private final TaskOutputPacker packer;
    private final TaskExecuter delegate;

    public SkipCachedTaskExecuter(TaskArtifactStateRepository repository, TaskOutputCache cache, TaskOutputPacker packer, TaskExecuter delegate) {
        this.repository = repository;
        this.cache = cache;
        this.packer = packer;
        this.delegate = delegate;
        SingleMessageLogger.incubatingFeatureUsed("Task output caching");
        LOGGER.info("Using {}", cache.getDescription());
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Clock clock = new Clock();

        TaskOutputsInternal taskOutputs = task.getOutputs();
        boolean cacheAllowed = taskOutputs.isCacheAllowed();

        boolean shouldCache;
        try {
            shouldCache = cacheAllowed && taskOutputs.isCacheEnabled();
        } catch (Exception t) {
            state.executed(new GradleException(String.format("Could not evaluate TaskOutputs.isCacheEnabled() for %s.", task), t));
            return;
        }

        LOGGER.debug("Determining if {} is cached already", task);

        TaskCacheKey cacheKey = null;
        if (shouldCache) {
            TaskArtifactState taskState = repository.getStateFor(task);
            try {
                cacheKey = taskState.calculateCacheKey();
                LOGGER.debug("Cache key for {} is {}", task, cacheKey);
            } catch (Exception e) {
                LOGGER.info(String.format("Could not build cache key for task %s", task), e);
            }
        } else {
            if (!cacheAllowed) {
                LOGGER.debug("Not caching {} as it is not allowed", task);
            } else {
                LOGGER.debug("Not caching {} as task output is not cacheable.", task);
            }
        }

        if (cacheKey != null) {
            try {
                TaskOutputReader cachedOutput = cache.get(cacheKey);
                if (cachedOutput != null) {
                    packer.unpack(taskOutputs, cachedOutput);
                    LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getTime());
                    state.upToDate("CACHED");
                    return;
                }
            } catch (IOException e) {
                LOGGER.info(String.format("Could not load cached output for %s with cache key %s", task, cacheKey), e);
            }
        }

        delegate.execute(task, state, context);

        if (cacheKey != null && state.getFailure() == null) {
            try {
                TaskOutputWriter cachedOutput = packer.createWriter(taskOutputs);
                cache.put(cacheKey, cachedOutput);
            } catch (IOException e) {
                LOGGER.info(String.format("Could not cache results for %s for cache key %s", task, cacheKey), e);
            }
        }
    }
}
