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

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationProgressEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.provider.events.AbstractOperationResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultOperationDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultOperationFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultSuccessResult;

import java.util.Collections;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildOperationListener implements BuildOperationListener {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingBuildOperationListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        eventConsumer.dispatch(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toBuildOperationDescriptor(buildOperation)));
    }

    @Override
    public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        eventConsumer.dispatch(new DefaultOperationFinishedProgressEvent(result.getEndTime(), toBuildOperationDescriptor(buildOperation), adaptResult(result)));
    }

    private DefaultOperationDescriptor toBuildOperationDescriptor(BuildOperationDescriptor buildOperation) {
        Object id = buildOperation.getId();
        String name = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        Object parentId = buildOperation.getParentId();
        return new DefaultOperationDescriptor(id, name, displayName, parentId);
    }

    private AbstractOperationResult adaptResult(OperationFinishEvent result) {
        Throwable failure = result.getFailure();
        long startTime = result.getStartTime();
        long endTime = result.getEndTime();
        if (failure != null) {
            return new DefaultFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
        }
        return new DefaultSuccessResult(startTime, endTime);
    }
}
