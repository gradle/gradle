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
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.caching.BuildCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.tasks.TaskOutputPacker;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

public class SkipCachedTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipCachedTaskExecuter.class);

    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final TaskOutputPacker packer;
    private final TaskExecuter delegate;
    private final TaskOutputsGenerationListener taskOutputsGenerationListener;
    private final TaskOutputOriginFactory taskOutputOriginFactory;
    private BuildCache cache;

    public SkipCachedTaskExecuter(TaskOutputOriginFactory taskOutputOriginFactory, BuildCacheConfigurationInternal buildCacheConfiguration, TaskOutputPacker packer, TaskOutputsGenerationListener taskOutputsGenerationListener, TaskExecuter delegate) {
        this.taskOutputOriginFactory = taskOutputOriginFactory;
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.packer = packer;
        this.taskOutputsGenerationListener = taskOutputsGenerationListener;
        this.delegate = delegate;
    }

    @Override
    public void execute(final TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        final Timer clock = Timers.startTimer();

        final TaskOutputsInternal taskOutputs = task.getOutputs();
        LOGGER.debug("Determining if {} is cached already", task);

        BuildCacheKey cacheKey = null;
        if (state.getTaskOutputCaching().isEnabled()) {
            TaskArtifactState taskState = context.getTaskArtifactState();
            try {
                cacheKey = taskState.calculateCacheKey();
                LOGGER.info("Cache key for {} is {}", task, cacheKey);
            } catch (Exception e) {
                throw new GradleException(String.format("Could not build cache key for %s.", task), e);
            }

            if (buildCacheConfiguration.isPullAllowed()) {
                if (cacheKey != null) {
                    if (taskState.isAllowedToUseCachedResults()) {
                        boolean found = getCache().load(cacheKey, new BuildCacheEntryReader() {
                            @Override
                            public void readFrom(final InputStream input) {
                                taskOutputsGenerationListener.beforeTaskOutputsGenerated();
                                packer.unpack(taskOutputs, input, taskOutputOriginFactory.createReader(task));
                                LOGGER.info("Unpacked output for {} from cache (took {}).", task, clock.getElapsed());
                            }
                        });
                        if (found) {
                            state.setOutcome(TaskExecutionOutcome.FROM_CACHE);
                            return;
                        }
                    } else {
                        LOGGER.info("Not loading {} from cache because pulling from cache is disabled for this task", task);
                    }
                } else {
                    LOGGER.info("Not caching {} because no valid cache key was generated", task);
                }
            } else {
                LOGGER.debug("Not loading {} from cache because pulling from cache is disabled for this build", task);
            }
        }

        delegate.execute(task, state, context);

        if (cacheKey != null) {
            if (buildCacheConfiguration.isPushAllowed()) {
                if (state.getFailure() == null) {
                    getCache().store(cacheKey, new BuildCacheEntryWriter() {
                        @Override
                        public void writeTo(OutputStream output) {
                            LOGGER.info("Packing {}", task.getPath());
                            packer.pack(taskOutputs, output, taskOutputOriginFactory.createWriter(task, clock.getElapsedMillis()));
                        }
                    });
                } else {
                    LOGGER.debug("Not pushing result from {} to cache because the task failed", task);
                }
            } else {
                LOGGER.debug("Not pushing results from {} to cache because pushing to cache is disabled for this build", task);
            }
        } else {
            LOGGER.info("Not pushing results from {} to cache because no valid cache key was generated", task);
        }
    }

    private synchronized BuildCache getCache() {
        if (cache == null) {
            cache = buildCacheConfiguration.getCache();
            LOGGER.info("Using {}", cache.getDescription());
        }
        return cache;
    }
}
