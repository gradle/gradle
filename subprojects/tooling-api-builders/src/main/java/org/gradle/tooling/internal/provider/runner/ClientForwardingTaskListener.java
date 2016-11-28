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

import org.gradle.api.execution.internal.TaskOperationDescriptor;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTaskResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTaskDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTaskFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSuccessResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskListener implements InternalBuildListener {
    private final BuildEventConsumer eventConsumer;
    private final BuildClientSubscriptions clientSubscriptions;
    private final InternalBuildListener delegate;
    private final Set<Object> skipEvents = new HashSet<Object>();

    ClientForwardingTaskListener(BuildEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, InternalBuildListener delegate) {
        this.eventConsumer = eventConsumer;
        this.clientSubscriptions = clientSubscriptions;
        this.delegate = delegate;
    }

    @Override
    public void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
        if (skipEvents.contains(buildOperation.getParentId())) {
            skipEvents.add(buildOperation.getId());
            return;
        }

        if (buildOperation.getOperationDescriptor() instanceof TaskOperationDescriptor) {
            if (clientSubscriptions.isSendTaskProgressEvents()) {
                TaskInternal task = ((TaskOperationDescriptor) buildOperation.getOperationDescriptor()).getTask();
                eventConsumer.dispatch(new DefaultTaskStartedProgressEvent(startEvent.getStartTime(), toTaskDescriptor(buildOperation, task)));
            } else {
                // Discard this operation and all children
                skipEvents.add(buildOperation.getId());
            }
        } else {
            delegate.started(buildOperation, startEvent);
        }
    }

    @Override
    public void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
        if (skipEvents.remove(buildOperation.getId())) {
            return;
        }

        if (buildOperation.getOperationDescriptor() instanceof TaskOperationDescriptor) {
            TaskInternal task = ((TaskOperationDescriptor) buildOperation.getOperationDescriptor()).getTask();
            eventConsumer.dispatch(new DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), toTaskDescriptor(buildOperation, task), toTaskResult(task, finishEvent)));
        } else {
            delegate.finished(buildOperation, finishEvent);
        }
    }

    private DefaultTaskDescriptor toTaskDescriptor(BuildOperationInternal buildOperation, TaskInternal task) {
        Object id = buildOperation.getId();
        String taskIdentityPath = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        String taskPath = task.getIdentityPath().toString();
        Object parentId = getParentId(buildOperation);
        return new DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parentId);
    }

    private Object getParentId(BuildOperationInternal buildOperation) {
        // only set the BuildOperation as the parent if the Tooling API Consumer is listening to build progress events
        return clientSubscriptions.isSendBuildProgressEvents() ? buildOperation.getParentId() : null;
    }

    private static AbstractTaskResult toTaskResult(TaskInternal task, OperationResult result) {
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
