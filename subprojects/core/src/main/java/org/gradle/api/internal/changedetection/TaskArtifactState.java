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
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;

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

    /**
     * Returns the incremental task inputs for the current execution.
     */
    IncrementalTaskInputs getInputChanges();

    /**
     * Returns fingerprints of all the current input files.
     */
    Iterable<? extends FileCollectionFingerprint> getCurrentInputFileFingerprints();

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
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterTaskExecution(TaskExecutionContext taskExecutionContext);

    /**
     * Called when outputs were generated.
     */
    void persistNewOutputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata);

    /**
     * Returns the history for this task.
     */
    TaskExecutionHistory getExecutionHistory();

    /**
     * Returns the current output file fingerprints indexed by property name.
     */
    Map<String, CurrentFileCollectionFingerprint> getOutputFingerprints();

    /**
     * Returns if overlapping outputs were detected
     */
    @Nullable
    OverlappingOutputs getOverlappingOutputs();
}
