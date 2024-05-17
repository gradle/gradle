/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Maps build operations of a particular type into progress events to forward to the tooling API client.
 */
public interface BuildOperationMapper<DETAILS, TO extends InternalOperationDescriptor> {
    boolean isEnabled(BuildEventSubscriptions subscriptions);

    Class<DETAILS> getDetailsType();

    /**
     * Returns the trackers that are used by this mapper. If this mapper is enabled, then the trackers should be notified of
     * build operation execution. If this mapper is not enabled, the trackers can be ignored.
     */
    default List<? extends BuildOperationTracker> getTrackers() {
        return Collections.emptyList();
    }

    /**
     * Maps the descriptor for the given build operation.
     */
    TO createDescriptor(DETAILS details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent);

    /**
     * Maps the start event for the given build operation.
     */
    InternalOperationStartedProgressEvent createStartedEvent(TO descriptor, DETAILS details, OperationStartEvent startEvent);

    /**
     * Maps the finish event for the given build operation.
     */
    InternalOperationFinishedProgressEvent createFinishedEvent(TO descriptor, DETAILS details, OperationFinishEvent finishEvent);

    /**
     * Maps the given progress event. Returns {@code null} of the event should be discarded and not forwarded to the client.
     */
    @Nullable
    default InternalProgressEvent createProgressEvent(TO descriptor, OperationProgressEvent progressEvent) {
        return null;
    }
}
