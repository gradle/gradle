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

import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationDescriptor;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultSuccessResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingBuildOperationListener implements BuildOperationListener {
    // A map from progress operation id seen in event -> progress operation id that should be forwarded
    private final Map<OperationIdentifier, OperationIdentifier> effective = new ConcurrentHashMap<>();
    // A set of progress operations that have been forwarded
    private final Map<OperationIdentifier, DefaultOperationDescriptor> forwarded = new ConcurrentHashMap<>();

    private final ProgressEventConsumer eventConsumer;

    ClientForwardingBuildOperationListener(ProgressEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier parentId = buildOperation.getParentId();
        if (shouldForward(buildOperation)) {
            DefaultOperationDescriptor descriptor = new DefaultOperationDescriptor(
                id,
                buildOperation.getName(),
                buildOperation.getDisplayName(),
                parentId == null
                    ? null
                    : effective.getOrDefault(parentId, parentId)
            );
            forwarded.put(id, descriptor);
            eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor));
        } else {
            // Ignore this operation, and map any reference to it to its parent (or whatever its parent is mapped to
            OperationIdentifier mappedParent = effective.get(parentId);
            if (mappedParent == null) {
                mappedParent = parentId;
            }
            effective.put(id, mappedParent);
        }
    }

    private static boolean shouldForward(BuildOperationDescriptor buildOperation) {
        return buildOperation.getMetadata() != BuildOperationCategory.UNCATEGORIZED
            || buildOperation.getProgressDisplayName() != null;
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier mappedEvent = effective.remove(id);
        if (mappedEvent != null) {
            return;
        }
        DefaultOperationDescriptor descriptor = forwarded.remove(id);
        if (descriptor != null) {
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(result.getEndTime(), descriptor, toOperationResult(result)));
        }
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
