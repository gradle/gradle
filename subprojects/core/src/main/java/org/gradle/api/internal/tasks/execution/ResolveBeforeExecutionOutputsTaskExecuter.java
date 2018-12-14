/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.changes.TaskFingerprintUtil;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;

/**
 * Snapshot the task's inputs before execution.
 *
 * The data is required to determine overlapping outputs and to resolve the tasks full execution state before execution.
 */
public class ResolveBeforeExecutionOutputsTaskExecuter implements TaskExecuter {
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final TaskExecuter delegate;

    public ResolveBeforeExecutionOutputsTaskExecuter(FileCollectionFingerprinterRegistry fingerprinterRegistry, TaskExecuter delegate) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputsBeforeExecution = TaskFingerprintUtil.fingerprintTaskFiles(task, context.getTaskProperties().getOutputFileProperties(), fingerprinterRegistry);
        context.setOutputFilesBeforeExecution(outputsBeforeExecution);

        AfterPreviousExecutionState afterPreviousExecutionState = context.getAfterPreviousExecution();
        OverlappingOutputs overlappingOutputs = OverlappingOutputs.detect(
            afterPreviousExecutionState != null
                ? afterPreviousExecutionState.getOutputFileProperties()
                : null,
            outputsBeforeExecution
        );
        context.setOverlappingOutputs(overlappingOutputs);

        return delegate.execute(task, state, context);
    }
}
