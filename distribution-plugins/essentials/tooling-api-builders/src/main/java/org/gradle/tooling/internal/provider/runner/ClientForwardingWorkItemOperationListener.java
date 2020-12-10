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

import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultWorkItemDescriptor;
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType;

import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Work item listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 5.1
 */
class ClientForwardingWorkItemOperationListener extends SubtreeFilteringBuildOperationListener<ExecuteWorkItemBuildOperationType.Details> {

    ClientForwardingWorkItemOperationListener(ProgressEventConsumer eventConsumer, BuildEventSubscriptions clientSubscriptions, BuildOperationListener delegate) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.WORK_ITEM, ExecuteWorkItemBuildOperationType.Details.class);
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ExecuteWorkItemBuildOperationType.Details details) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toWorkItemDescriptor(buildOperation, details));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ExecuteWorkItemBuildOperationType.Details details) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toWorkItemDescriptor(buildOperation, details), toOperationResult(finishEvent));
    }

    private DefaultWorkItemDescriptor toWorkItemDescriptor(BuildOperationDescriptor buildOperation, ExecuteWorkItemBuildOperationType.Details details) {
        Object id = buildOperation.getId();
        String className = details.getClassName();
        String displayName = buildOperation.getDisplayName();
        Object parentId = eventConsumer.findStartedParentId(buildOperation);
        return new DefaultWorkItemDescriptor(id, className, displayName, parentId);
    }

}
