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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.tasks.MutatingTaskExecuter;
import org.gradle.api.internal.tasks.MutatingTaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

/**
 * A {@link TaskExecuter} which snaphots the task's output after it has executed.
 */
public class SnapshotAfterExecutionTaskExecuter implements TaskExecuter {
    private final TaskOutputFilesRepository outputFilesRepository;
    private final MutatingTaskExecuter delegate;

    public SnapshotAfterExecutionTaskExecuter(TaskOutputFilesRepository outputFilesRepository, MutatingTaskExecuter delegate) {
        this.outputFilesRepository = outputFilesRepository;
        this.delegate = delegate;
    }

    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        MutatingTaskExecuterResult result = delegate.execute(task, state, context);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> finalOutputs = result.getFinalOutputs();
        context.getTaskArtifactState().persistNewOutputs(
            finalOutputs,
            state.getFailure() == null,
            result.getOriginMetadata());
        outputFilesRepository.recordOutputs(finalOutputs.values());
        return result;
    }
}
