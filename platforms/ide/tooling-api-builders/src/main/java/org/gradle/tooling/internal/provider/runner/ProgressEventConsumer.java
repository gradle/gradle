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

import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
class ProgressEventConsumer {

    private final Set<Object> startedIds = ConcurrentHashMap.newKeySet();
    private final BuildEventConsumer delegate;
    private final BuildOperationAncestryTracker ancestryTracker;

    ProgressEventConsumer(BuildEventConsumer delegate, BuildOperationAncestryTracker ancestryTracker) {
        this.delegate = delegate;
        this.ancestryTracker = ancestryTracker;
    }

    @Nullable
    OperationIdentifier findStartedParentId(BuildOperationDescriptor operation) {
        return ancestryTracker
            .findClosestMatchingAncestor(operation.getParentId(), startedIds::contains)
            .orElse(null);
    }

    @Nullable
    OperationIdentifier findStartedParentId(OperationIdentifier identifier) {
        return ancestryTracker
            .findClosestMatchingAncestor(identifier, startedIds::contains)
            .orElse(null);
    }

    void started(InternalOperationStartedProgressEvent event) {
        delegate.dispatch(event);
        startedIds.add(event.getDescriptor().getId());
    }

    void finished(InternalOperationFinishedProgressEvent event) {
        startedIds.remove(event.getDescriptor().getId());
        delegate.dispatch(event);
    }

    void progress(InternalProgressEvent event) {
        delegate.dispatch(event);
    }
}
