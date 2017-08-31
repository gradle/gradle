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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.id.UniqueId;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

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
     * Returns the calculated cache key for the task's current state.
     */
    TaskOutputCachingBuildCacheKey calculateCacheKey();

    /**
     * Ensure snapshot is taken of the task's inputs and outputs before it is executed.
     */
    void ensureSnapshotBeforeTask();

    /**
     * Retakes output file snapshots and prevents the task from executing in an incremental fashion.
     */
    void afterOutputsRemovedBeforeTask();

    /**
     * Called on completion of task execution.
     */
    void snapshotAfterTaskExecution(Throwable failure);

    /**
     * Called on task being loaded from cache.
     */
    void snapshotAfterLoadedFromCache(ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot);

    /**
     * Returns the history for this task.
     */
    TaskExecutionHistory getExecutionHistory();

    /**
     * Returns the current output file content snapshots indexed by property name.
     */
    Map<String, Map<String, FileContentSnapshot>> getOutputContentSnapshots();

    /**
     * The ID of the build that created the outputs that might be reused.
     * Null if there are no previous executions, or outputs must not be reused (e.g. --rerun-tasks).
     * Never null if {@link #isUpToDate(Collection)} returns true.
     *
     * TODO: should this move to getExecutionHistory()?
     * @since 4.0
     */
    @Nullable
    UniqueId getOriginBuildInvocationId();
}
