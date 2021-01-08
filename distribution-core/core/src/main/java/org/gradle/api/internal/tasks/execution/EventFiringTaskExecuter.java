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
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CallableBuildOperation;

public class EventFiringTaskExecuter implements TaskExecuter {

    private final BuildOperationExecutor buildOperationExecutor;
    private final TaskExecutionListener taskExecutionListener;
    private final TaskListenerInternal taskListener;
    private final TaskExecuter delegate;

    public EventFiringTaskExecuter(BuildOperationExecutor buildOperationExecutor, TaskExecutionListener taskExecutionListener, TaskListenerInternal taskListener, TaskExecuter delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.taskExecutionListener = taskExecutionListener;
        this.taskListener = taskListener;
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(final TaskInternal task, final TaskStateInternal state, final TaskExecutionContext context) {
        return buildOperationExecutor.call(new CallableBuildOperation<TaskExecuterResult>() {
            @Override
            public TaskExecuterResult call(BuildOperationContext operationContext) {
                TaskExecuterResult result = executeTask(operationContext);
                operationContext.setStatus(state.getFailure() != null ? "FAILED" : state.getSkipMessage());
                operationContext.failed(state.getFailure());
                return result;
            }

            private TaskExecuterResult executeTask(BuildOperationContext operationContext) {
                Logger logger = task.getLogger();
                ContextAwareTaskLogger contextAwareTaskLogger = null;
                try {
                    taskListener.beforeExecute(task.getTaskIdentity());
                    taskExecutionListener.beforeExecute(task);
                    BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
                    if (logger instanceof ContextAwareTaskLogger) {
                        contextAwareTaskLogger = (ContextAwareTaskLogger) logger;
                        contextAwareTaskLogger.setFallbackBuildOperationId(currentOperation.getId());
                    }
                } catch (Throwable t) {
                    state.setOutcome(new TaskExecutionException(task, t));
                    return TaskExecuterResult.WITHOUT_OUTPUTS;
                }

                TaskExecuterResult result = delegate.execute(task, state, context);

                if (contextAwareTaskLogger != null) {
                    contextAwareTaskLogger.setFallbackBuildOperationId(null);
                }
                operationContext.setResult(new ExecuteTaskBuildOperationResult(
                    state,
                    result.getCachingState(),
                    result.getReusedOutputOriginMetadata().orElse(null),
                    result.executedIncrementally(),
                    result.getExecutionReasons()
                ));

                try {
                    taskExecutionListener.afterExecute(task, state);
                    taskListener.afterExecute(task.getTaskIdentity(), state);
                } catch (Throwable t) {
                    state.addFailure(new TaskExecutionException(task, t));
                }

                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                ExecuteTaskBuildOperationDetails taskOperation = new ExecuteTaskBuildOperationDetails(context.getLocalTaskNode());
                return BuildOperationDescriptor.displayName("Task " + task.getIdentityPath())
                    .name(task.getIdentityPath().toString())
                    .progressDisplayName(task.getIdentityPath().toString())
                    .metadata(BuildOperationCategory.TASK)
                    .details(taskOperation);
            }
        });
    }
}
