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

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.cache.TaskCacheKey;
import org.gradle.api.internal.tasks.cache.TaskOutputCache;
import org.gradle.api.internal.tasks.cache.TaskOutputPacker;
import org.gradle.api.internal.tasks.cache.TaskOutputReader;
import org.gradle.api.internal.tasks.cache.TaskOutputWriter;
import org.gradle.api.internal.tasks.cache.config.TaskCachingInternal;
import org.gradle.util.Clock;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final TaskCachingInternal taskCaching;
    private final StartParameter startParameter;
    private final TaskOutputPacker packer;
    private final TaskExecuter delegate;
    private TaskOutputCache cache;
    private final TaskOutputsGenerationListener taskOutputsGenerationListener;

    public SkipCachedTaskExecuter(TaskCachingInternal taskCaching, TaskOutputPacker packer, StartParameter startParameter, TaskOutputsGenerationListener taskOutputsGenerationListener, TaskExecuter delegate) {
        this.taskCaching = taskCaching;
        this.startParameter = startParameter;
        this.packer = packer;
        this.taskOutputsGenerationListener = taskOutputsGenerationListener;
        this.delegate = delegate;
        SingleMessageLogger.incubatingFeatureUsed("Task output caching");
    }

    @Override
    public void execute(final TaskInternal task, final TaskStateInternal state, TaskExecutionContext context) {
        final Clock clock = new Clock();

        final TaskOutputsInternal taskOutputs = task.getOutputs();

        boolean cacheEnabled;
        try {
            cacheEnabled = taskOutputs.isCacheEnabled();
        } catch (Exception t) {
            throw new GradleException(String.format("Could not evaluate TaskOutputs.cacheIf for %s.", task), t);
        }

        LOGGER.debug("Determining if {} is cached already", task);

        TaskCacheKey cacheKey = null;
        boolean cacheable = false;
        try {
            if (cacheEnabled) {
                if (taskOutputs.hasDeclaredOutputs()) {
                    if (taskOutputs.isCacheAllowed()) {
                        cacheable = true;
                        TaskArtifactState taskState = context.getTaskArtifactState();
                        try {
                            cacheKey = taskState.calculateCacheKey();
                            LOGGER.info("Cache key for {} is {}", task, cacheKey);
                        } catch (Exception e) {
                            throw new GradleException(String.format("Could not build cache key for %s.", task), e);
                        }

                        if (cacheKey != null) {
                            if (taskState.isAllowedToUseCachedResults()) {
                                try {
                                    boolean found = getCache().load(cacheKey, new TaskOutputReader() {
                                        @Override
                                        public void readFrom(InputStream input) throws IOException {
                                            packer.unpack(taskOutputs, input);
                                            LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getTime());
                                        }
                                    });
                                    if (found) {
                                        state.setOutcome(TaskExecutionOutcome.FROM_CACHE);
                                        taskOutputsGenerationListener.beforeTaskOutputsGenerated();
                                        return;
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn("Could not load cached output for {} with cache key {}", task, cacheKey, e);
                                }
                            } else {
                                LOGGER.info("Not loading {} from cache because loading from cache is disabled", task);
                            }
                        } else {
                            LOGGER.info("Not caching {} because no valid cache key was generated", task);
                        }
                    } else {
                        LOGGER.info("Not caching {} because it declares multiple output files for a single output property via `@OutputFiles`, `@OutputDirectories` or `TaskOutputs.files()`", task);
                    }
                } else {
                    LOGGER.info("Not caching {} as task has declared no outputs", task);
                }
            } else {
                LOGGER.debug("Not caching {} as task output is not cacheable.", task);
            }
        } finally {
            state.setCacheable(cacheable);
        }

        delegate.execute(task, state, context);

        if (cacheKey != null && state.getFailure() == null) {
            try {
                getCache().store(cacheKey, new TaskOutputWriter() {
                    @Override
                    public void writeTo(OutputStream output) throws IOException {
                        packer.pack(taskOutputs, output);
                    }
                });
            } catch (Exception e) {
                LOGGER.warn("Could not cache results for {} for cache key {}", task, cacheKey, e);
            }
        }
    }

    private TaskOutputCache getCache() {
        if (cache == null) {
            cache = taskCaching.getCacheFactory().createCache(startParameter);
            LOGGER.info("Using {}", cache.getDescription());
        }
        return cache;
    }
}
