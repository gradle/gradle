/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import org.gradle.api.Nullable;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.BuildCacheKey;

import java.util.Collection;

/**
 * Encapsulates the state of the task when its outputs were last generated.
 */
public interface TaskArtifactState {
    /**
     * Returns true if the task outputs were generated using the given task inputs.
     *
     * @param messages a collection to add messages which explain why the task is out-of-date.
     */
    boolean isUpToDate(Collection<String> messages);

    IncrementalTaskInputs getInputChanges();

    /**
     * Returns whether it is okay to use results loaded from cache instead of executing the task.
     */
    boolean isAllowedToUseCachedResults();

    /**
     * Returns the calculated cache key for the task's current state, or {@code null} if the task is not cacheable.
     */
    @Nullable
    BuildCacheKey calculateCacheKey();

    /**
     * Called before the task is to be executed. Note that {@link #isUpToDate(java.util.Collection)} may not necessarily have been called.
     */
    void beforeTask();

    /**
     * Called on successful completion of task execution.
     */
    void afterTask();

    /**
     * Called when this state is finished with.
     */
    void finished();

    /**
     * Returns the history for this task.
     */
    TaskExecutionHistory getExecutionHistory();
}
