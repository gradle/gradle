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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultWorkItemDescriptor;
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType;

import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Work item listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 5.1
 */
class ClientForwardingWorkItemOperationListener implements BuildOperationListener {

    private final ProgressEventConsumer eventConsumer;
    private final BuildOperationListener delegate;

    ClientForwardingWorkItemOperationListener(ProgressEventConsumer eventConsumer, BuildOperationListener delegate) {
        this.eventConsumer = eventConsumer;
        this.delegate = delegate;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ExecuteWorkItemBuildOperationType.Details) {
            ExecuteWorkItemBuildOperationType.Details details = (ExecuteWorkItemBuildOperationType.Details) buildOperation.getDetails();
            eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toWorkItemDescriptor(buildOperation, details)));
        } else {
            delegate.started(buildOperation, startEvent);
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof ExecuteWorkItemBuildOperationType.Details) {
            ExecuteWorkItemBuildOperationType.Details details = (ExecuteWorkItemBuildOperationType.Details) buildOperation.getDetails();
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toWorkItemDescriptor(buildOperation, details), toOperationResult(finishEvent)));
        } else {
            delegate.finished(buildOperation, finishEvent);
        }
    }

    private DefaultWorkItemDescriptor toWorkItemDescriptor(BuildOperationDescriptor buildOperation, ExecuteWorkItemBuildOperationType.Details details) {
        Object id = buildOperation.getId();
        String className = details.getClassName();
        String displayName = buildOperation.getDisplayName();
        Object parentId = eventConsumer.findStartedParentId(buildOperation.getParentId());
        return new DefaultWorkItemDescriptor(id, className, displayName, parentId);
    }

}
