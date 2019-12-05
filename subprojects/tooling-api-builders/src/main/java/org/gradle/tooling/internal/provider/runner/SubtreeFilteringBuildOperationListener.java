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

import com.google.common.collect.Sets;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import java.util.Set;

/**
 * Build operation listener responsible for a certain operation type that is detected by
 * checking for a specific details class.
 *
 * <p>If the operation type is not requested, all events in the corresponding subtree
 * are skipped, i.e. neither reported to the consumer nor passed on to the delegate.
 *
 * @since 5.1
 */
abstract class SubtreeFilteringBuildOperationListener<D> implements BuildOperationListener {

    protected final ProgressEventConsumer eventConsumer;
    private final BuildOperationListener delegate;
    private final Class<D> detailsClass;

    // BuildOperationListener dispatch is not serialized
    private final Set<Object> skipEvents = Sets.newConcurrentHashSet();
    private final boolean enabled;

    SubtreeFilteringBuildOperationListener(ProgressEventConsumer eventConsumer, BuildEventSubscriptions clientSubscriptions, BuildOperationListener delegate, OperationType operationType, Class<D> detailsClass) {
        this.eventConsumer = eventConsumer;
        this.delegate = delegate;
        this.detailsClass = detailsClass;
        this.enabled = clientSubscriptions.isRequested(operationType);
    }

    protected boolean isEnabled() {
        return enabled;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier parentId = buildOperation.getParentId();
        if (parentId != null && skipEvents.contains(parentId)) {
            skipEvents.add(buildOperation.getId());
            return;
        }

        if (detailsClass.isInstance(buildOperation.getDetails())) {
            if (enabled) {
                D details = detailsClass.cast(buildOperation.getDetails());
                eventConsumer.started(toStartedEvent(buildOperation, startEvent, details));
            } else {
                // Discard this operation and all children
                skipEvents.add(buildOperation.getId());
            }
        } else {
            delegate.started(buildOperation, startEvent);
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (skipEvents.remove(buildOperation.getId())) {
            return;
        }

        if (detailsClass.isInstance(buildOperation.getDetails())) {
            D details = detailsClass.cast(buildOperation.getDetails());
            eventConsumer.finished(toFinishedEvent(buildOperation, finishEvent, details));
        } else {
            delegate.finished(buildOperation, finishEvent);
        }
    }

    protected abstract InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, D details);

    protected abstract InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, D details);

}
