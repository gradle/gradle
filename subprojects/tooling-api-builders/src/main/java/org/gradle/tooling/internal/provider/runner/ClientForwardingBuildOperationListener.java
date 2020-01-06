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

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationDescriptor;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultSuccessResult;

import java.util.Collections;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildOperationListener implements BuildOperationListener {

    private final ProgressEventConsumer eventConsumer;

    ClientForwardingBuildOperationListener(ProgressEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toBuildOperationDescriptor(buildOperation)));
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        eventConsumer.finished(new DefaultOperationFinishedProgressEvent(result.getEndTime(), toBuildOperationDescriptor(buildOperation), toOperationResult(result)));
    }

    private DefaultOperationDescriptor toBuildOperationDescriptor(BuildOperationDescriptor buildOperation) {
        Object id = buildOperation.getId();
        String name = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        Object parentId = buildOperation.getParentId();
        return new DefaultOperationDescriptor(id, name, displayName, parentId);
    }

    static AbstractOperationResult toOperationResult(OperationFinishEvent result) {
        Throwable failure = result.getFailure();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();
        if (failure != null) {
            return new DefaultFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
        }
        return new DefaultSuccessResult(startTime, endTime);
    }
}
