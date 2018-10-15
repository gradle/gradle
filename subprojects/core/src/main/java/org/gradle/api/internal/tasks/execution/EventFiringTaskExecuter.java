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

import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class EventFiringTaskExecuter implements TaskExecuter {

    private final BuildOperationExecutor buildOperationExecutor;
    private final TaskExecutionListener taskExecutionListener;
    private final TaskExecuter delegate;

    public EventFiringTaskExecuter(BuildOperationExecutor buildOperationExecutor, TaskExecutionListener taskExecutionListener, TaskExecuter delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.taskExecutionListener = taskExecutionListener;
        this.delegate = delegate;
    }

    @Override
    public void execute(final TaskInternal task, final TaskStateInternal state, final TaskExecutionContext context) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext operationContext) {
                taskExecutionListener.beforeExecute(task);

                delegate.execute(task, state, context);
                // Make sure we set the result even if listeners fail to execute
                operationContext.setResult(new ExecuteTaskBuildOperationResult(state, context));

                // If this fails, it masks the task failure.
                // It should addSuppressed() the task failure if there was one.
                taskExecutionListener.afterExecute(task, state);

                operationContext.setStatus(state.getFailure() != null ? "FAILED" : state.getSkipMessage());
                operationContext.failed(state.getFailure());
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                ExecuteTaskBuildOperationDetails taskOperation = new ExecuteTaskBuildOperationDetails(task);
                return BuildOperationDescriptor.displayName("Task " + task.getIdentityPath())
                    .name(task.getIdentityPath().toString())
                    .progressDisplayName(task.getIdentityPath().toString())
                    .operationType(BuildOperationCategory.TASK)
                    .details(taskOperation);
            }
        });
    }
}
