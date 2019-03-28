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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationResult;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.ExecutingBuildOperation;

import java.util.function.Consumer;

/**
 * The operation started here is finished in {@link MarkSnapshottingInputsFinishedStep}.
 *
 * @see SnapshotTaskInputsBuildOperationResult
 */
public class StartSnapshotTaskInputsBuildOperationTaskExecuter implements TaskExecuter {
    private static final SnapshotTaskInputsBuildOperationType.Details DETAILS_INSTANCE = new SnapshotTaskInputsBuildOperationType.Details() {};

    private static final String BUILD_OPERATION_NAME = "Snapshot task inputs";

    private final BuildOperationExecutor buildOperationExecutor;
    private final TaskExecuter delegate;

    public StartSnapshotTaskInputsBuildOperationTaskExecuter(
        BuildOperationExecutor buildOperationExecutor,
        TaskExecuter delegate
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        ExecutingBuildOperation operation = buildOperationExecutor.start(BuildOperationDescriptor
            .displayName(BUILD_OPERATION_NAME + " for " + task.getIdentityPath())
            .name(BUILD_OPERATION_NAME)
            .details(DETAILS_INSTANCE));
        context.setSnapshotTaskInputsBuildOperation(operation);
        try {
            return delegate.execute(task, state, context);
        } finally {
            // If the operation hasn't finished normally (because of a shortcut or an error), we close it without a cache key
            context.removeSnapshotTaskInputsBuildOperation().ifPresent(new Consumer<ExecutingBuildOperation>() {
                @Override
                public void accept(ExecutingBuildOperation operation) {
                    operation.setResult(new SnapshotTaskInputsBuildOperationResult(CachingState.NOT_DETERMINED));
                }
            });
        }
    }
}
