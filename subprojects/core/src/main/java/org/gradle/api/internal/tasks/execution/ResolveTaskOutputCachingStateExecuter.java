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

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputCachingState;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveTaskOutputCachingStateExecuter implements TaskExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveTaskOutputCachingStateExecuter.class);
    private final boolean taskOutputCacheEnabled;
    private final TaskExecuter delegate;

    public ResolveTaskOutputCachingStateExecuter(boolean taskOutputCacheEnabled, TaskExecuter delegate) {
        this.taskOutputCacheEnabled = taskOutputCacheEnabled;
        this.delegate = delegate;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (taskOutputCacheEnabled) {
            try {
                TaskOutputCachingState taskOutputCachingState = task.getOutputs().getCachingState();
                state.setTaskOutputCaching(taskOutputCachingState);
                if (!taskOutputCachingState.isEnabled()) {
                    LOGGER.info("Caching disabled for {}: {}", task, taskOutputCachingState.getDisabledReason());
                }
            } catch (Exception t) {
                throw new GradleException(String.format("Could not evaluate TaskOutputs.getCachingState().isEnabled() for %s.", task), t);
            }
        } else {
            state.setTaskOutputCaching(DefaultTaskOutputs.DISABLED);
        }
        delegate.execute(task, state, context);
    }
}
