/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTaskResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTaskDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTaskFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSuccessResult;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import java.util.Collections;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskOperationListener extends SubtreeFilteringBuildOperationListener<ExecuteTaskBuildOperationDetails> {

    private final OperationResultPostProcessor operationResultPostProcessor;

    ClientForwardingTaskOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate, OperationResultPostProcessor operationResultPostProcessor) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.TASK, ExecuteTaskBuildOperationDetails.class);
        this.operationResultPostProcessor = operationResultPostProcessor;
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ExecuteTaskBuildOperationDetails details) {
        return new DefaultTaskStartedProgressEvent(startEvent.getStartTime(), toTaskDescriptor(buildOperation, details.getTask()));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ExecuteTaskBuildOperationDetails details) {
        TaskInternal task = details.getTask();
        AbstractTaskResult result = operationResultPostProcessor.process(toTaskResult(task, finishEvent), buildOperation.getId());
        return new DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), toTaskDescriptor(buildOperation, task), result);
    }

    private DefaultTaskDescriptor toTaskDescriptor(BuildOperationDescriptor buildOperation, TaskInternal task) {
        Object id = buildOperation.getId();
        String taskIdentityPath = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        String taskPath = task.getIdentityPath().toString();
        Object parentId = eventConsumer.findStartedParentId(buildOperation.getParentId());
        return new DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parentId);
    }

    private static AbstractTaskResult toTaskResult(TaskInternal task, OperationFinishEvent result) {
        TaskStateInternal state = task.getState();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();

        if (state.getUpToDate()) {
            return new DefaultTaskSuccessResult(startTime, endTime, true, state.isFromCache(), state.getSkipMessage());
        } else if (state.getSkipped()) {
            return new DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage());
        } else {
            Throwable failure = result.getFailure();
            if (failure == null) {
                return new DefaultTaskSuccessResult(startTime, endTime, false, state.isFromCache(), "SUCCESS");
            } else {
                return new DefaultTaskFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
            }
        }
    }

}
