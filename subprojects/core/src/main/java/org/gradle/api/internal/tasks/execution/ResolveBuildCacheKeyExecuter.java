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
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.util.Path;

import java.util.function.Function;

public class ResolveBuildCacheKeyExecuter implements TaskExecuter {

    private static final Logger LOGGER = Logging.getLogger(ResolveBuildCacheKeyExecuter.class);
    private static final BuildCacheKeyInputs NO_CACHE_KEY_INPUTS = new BuildCacheKeyInputs(
        null,
        null,
        null,
        null,
        null,
        null
    );

    public static final TaskOutputCachingBuildCacheKey NO_CACHE_KEY = new TaskOutputCachingBuildCacheKey() {
        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public String toString() {
            return "INVALID";
        }

        @Override
        public Path getTaskPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getHashCode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BuildCacheKeyInputs getInputs() {
            return NO_CACHE_KEY_INPUTS;
        }

        @Override
        public byte[] getHashCodeBytes() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return toString();
        }
    };

    private final TaskCacheKeyCalculator calculator;
    private final boolean buildCacheDebugLogging;
    private final TaskExecuter delegate;

    public ResolveBuildCacheKeyExecuter(
        TaskCacheKeyCalculator calculator,
        boolean buildCacheDebugLogging,
        TaskExecuter delegate
    ) {
        this.calculator = calculator;
        this.buildCacheDebugLogging = buildCacheDebugLogging;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskOutputCachingBuildCacheKey cacheKey = resolve(task, context);
        context.setBuildCacheKey(cacheKey);
        return delegate.execute(task, state, context);
    }

    private TaskOutputCachingBuildCacheKey resolve(final TaskInternal task, TaskExecutionContext context) {
        final TaskProperties properties = context.getTaskProperties();
        return context.getBeforeExecutionState()
            .map(new Function<BeforeExecutionState, TaskOutputCachingBuildCacheKey>() {
                @Override
                public TaskOutputCachingBuildCacheKey apply(BeforeExecutionState beforeExecutionState) {
                    TaskOutputCachingBuildCacheKey cacheKey = calculator.calculate(task, beforeExecutionState, properties, buildCacheDebugLogging);
                    if (properties.hasDeclaredOutputs() && cacheKey.isValid()) { // A task with no outputs has no cache key.
                        LogLevel logLevel = buildCacheDebugLogging ? LogLevel.LIFECYCLE : LogLevel.INFO;
                        LOGGER.log(logLevel, "Build cache key for {} is {}", task, cacheKey.getHashCode());
                    }
                    return cacheKey;
                }
            })
            .orElse(NO_CACHE_KEY);
    }
}
